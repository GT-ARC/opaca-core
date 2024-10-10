package de.gtarc.opaca.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Simple class for connecting to the OPACA "/subscribe" Websocket for a specific topic.
 */
public class WebSocketConnector {

    public static CompletableFuture<WebSocket> subscribe(String runtimePlatformUrl, String topic, Consumer<String> callback) {        
        URI endpoint = URI.create(runtimePlatformUrl.replaceAll("^http", "ws") + "/subscribe");
        WebSocket.Listener listener = new WebSocket.Listener() {
            
            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.sendText(topic, true);
                WebSocket.Listener.super.onOpen(webSocket);
            }
            
            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence message, boolean last) {
                callback.accept(message.toString());
                return WebSocket.Listener.super.onText(webSocket, message, last);
            }
        };
        return HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(endpoint, listener);
    }

}