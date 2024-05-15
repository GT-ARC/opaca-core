package de.gtarc.opaca.util;

import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.util.component.LifeCycle;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class WebSocketConnectionManager {

    private static final int RECONNECT_DELAY = 1000;  // Reconnect delay in milliseconds
    private static final long PING_INTERVAL = 10000;  // Ping interval in milliseconds
    private static final int MAX_RETRIES = 100;       // Maximum retry attempts
    private static final List<MessageListener> listeners = new CopyOnWriteArrayList<>();

    public interface MessageListener {
        void onMessage(String message);
    }

    public static void connectToWebSocket(String runtimePlatformUrl) {
        HttpClient httpClient = new HttpClient();
        WebSocketClient client = new WebSocketClient(httpClient);
        try {
            client.setMaxTextMessageSize(8 * 1024);

            httpClient.start();
            client.start();

            String wsUrl = runtimePlatformUrl.replace("http://", "ws://").replace("https://", "wss://");
            URI endpoint = URI.create(wsUrl + "/subscribe");
            AgentWebSocketClient socket = new AgentWebSocketClient("/invoke");
            ClientUpgradeRequest request = new ClientUpgradeRequest();

            connectAndListen(client, socket, endpoint, request);
        } catch (Exception e) {
            System.err.println("Initialization failed: " + e.getMessage());
        }
    }

    private static void connectAndListen(WebSocketClient client, AgentWebSocketClient socket, URI endpoint, ClientUpgradeRequest request) {
        new Thread(() -> {
            int retryCount = 0;
            while (!Thread.currentThread().isInterrupted() && retryCount < MAX_RETRIES) {
                try {
                    client.connect(socket, endpoint, request).get();
                    socket.awaitClose();
                    retryCount = 0;
                } catch (Exception e) {
                    System.err.println("WebSocket connection failed: " + e.getMessage());
                    retryCount++;
                } finally {
                    disconnectClient(client);
                }
                if (retryCount < MAX_RETRIES) {
                    try {
                        Thread.sleep(RECONNECT_DELAY);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            System.err.println("Reached maximum retry attempts, stopping reconnection attempts.");
        }).start();
    }

    private static void disconnectClient(WebSocketClient client) {
        try {
            if (client.isRunning()) {
                client.stop();
            }
        } catch (Exception e) {
            System.err.println("Failed to stop WebSocket client: " + e.getMessage());
        }
    }

    public static void addMessageListener(MessageListener listener) {
        listeners.add(listener);
    }

    public static void removeMessageListener(MessageListener listener) {
        listeners.remove(listener);
    }

    static void notifyListeners(String message) {
        for (MessageListener listener : listeners) {
            listener.onMessage(message);
        }
    }
}