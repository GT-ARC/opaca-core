package de.gtarc.opaca.platform.services;

import de.gtarc.opaca.api.ConnectionsApi;
import de.gtarc.opaca.model.ConnectionRequest;
import de.gtarc.opaca.platform.PlatformConfig;
import de.gtarc.opaca.platform.auth.AuthUtils;
import de.gtarc.opaca.platform.session.SessionData;
import de.gtarc.opaca.platform.util.Utils;
import de.gtarc.opaca.util.ApiProxy;
import de.gtarc.opaca.util.WebSocketConnector;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.http.WebSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

/**
 * Implementation of ConnectionsApi, responsible for managing connected OPACA Runtime Platforms.
 */
@Log4j2
@Service
public class ConnectionsService implements ConnectionsApi {

    @Autowired
    private SessionData sessionData;

    @Autowired
    private PlatformConfig config;

    @Autowired
    private AuthUtils authUtils;

    /** open websockets to connected platforms to receive notifications on changes */
    private final Map<String, WebSocket> connectionWebsockets = new HashMap<>();


    /*
     * API ROUTES
     */

    @Override
    public boolean connectPlatform(ConnectionRequest connect) throws IOException {
        String url = Utils.normalizeString(connect.getUrl());
        Utils.checkUrl(url);
        if (url.equals(config.getOwnBaseUrl()) || sessionData.connectedPlatforms.containsKey(url)) {
            return false;
        }
        // try to get info (with token, if given)
        var token = connect.getToken() != null ? connect.getToken() : authUtils.getPlatformToken();
        var client = getPlatformProxy(url, token);
        var info = client.getPlatformInfo();
        // ask the other platform to connect back to self?
        if (connect.isConnectBack()) {
            var ownUrl = config.getOwnBaseUrl();
            client.connectPlatform(new ConnectionRequest(ownUrl, false, null));
        }
        // use websocket to connect to updates
        openConnectionWebsocket(url);

        // store connection if all the above steps succeeded
        sessionData.connectedPlatforms.put(url, info);
        return true;
    }

    @Override
    public List<String> getConnections() {
        return List.copyOf(sessionData.connectedPlatforms.keySet());
    }

    @Override
    public boolean disconnectPlatform(ConnectionRequest disconnect) throws IOException {
        var url = Utils.normalizeString(disconnect.getUrl());
        Utils.checkUrl(url);
        if (sessionData.connectedPlatforms.containsKey(url)) {
            sessionData.connectedPlatforms.remove(url);
            if (connectionWebsockets.containsKey(url)) {
                var ws = connectionWebsockets.remove(url);
                ws.sendClose(1000, "disconnected");
            }
            // disconnect other?
            if (disconnect.isConnectBack()) {
                var client = getPlatformProxy(url, authUtils.getPlatformToken());
                var ownUrl = config.getOwnBaseUrl();
                client.disconnectPlatform(new ConnectionRequest(ownUrl, false, null));
            }
            log.info("Disconnected from {}", url);
            return true;
        }
        return false;
    }

    @Override
    public boolean notifyUpdatePlatform(String platformUrl) {
        platformUrl = Utils.normalizeString(platformUrl);
        Utils.checkUrl(platformUrl);
        if (platformUrl.equals(config.getOwnBaseUrl())) {
            log.warn("Cannot request update for self.");
            return false;
        }
        if (! sessionData.connectedPlatforms.containsKey(platformUrl)) {
            var msg = String.format("Platform was not connected: %s", platformUrl);
            throw new NoSuchElementException(msg);
        }
        try {
            var client = getPlatformProxy(platformUrl);
            var platformInfo = client.getPlatformInfo();
            sessionData.connectedPlatforms.put(platformUrl, platformInfo);
            return true;
        } catch (IOException e) {
            log.warn("Platform did not respond: {}; removing...", platformUrl);
            sessionData.connectedPlatforms.remove(platformUrl);
            return false;
        }
    }

    /*
     * HELPER METHODS
     */

    protected ApiProxy getPlatformProxy(String url) {
        return new ApiProxy(url, config.getOwnBaseUrl(), null);
    }

    protected ApiProxy getPlatformProxy(String url, String token) {
        return new ApiProxy(url, config.getOwnBaseUrl(), token);
    }

    /**
     * Create a Websocket connection and associate it with the connected platform's URL, to be closed when disconnected
     */
    public void openConnectionWebsocket(String url) {
        try {
            var token = authUtils.getPlatformToken();
            var res = WebSocketConnector.subscribe(url, token, "/containers", msg -> notifyUpdatePlatform(url));
            connectionWebsockets.put(url, res.get());
        } catch (ExecutionException | InterruptedException e) {
            log.warn("Failed to establish websocket connection to {}", url);
        }
    }

}
