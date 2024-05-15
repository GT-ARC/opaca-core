package de.gtarc.opaca.util;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;

import java.util.concurrent.CountDownLatch;

@WebSocket
public class AgentWebSocketClient {

    private Session session;
    private CountDownLatch latch = new CountDownLatch(1);
    private String initialMessage;

    public AgentWebSocketClient(String initialMessage) {
        this.initialMessage = initialMessage;
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        System.out.println("WebSocket opened: " + session.getRemoteAddress());
        this.session = session;
        try {
            session.getRemote().sendString(initialMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnWebSocketMessage
    public void onMessage(String message) {
        System.out.println("Received: " + message);
        WebSocketConnectionManager.notifyListeners(message);  // Notify listeners about the received message
        if ("close".equalsIgnoreCase(message.trim())) {
            latch.countDown();
        }
    }

    @OnWebSocketError
    public void onError(Throwable throwable) {
        System.out.println("Error: " + throwable.getLocalizedMessage());
        latch.countDown();
    }

    public void awaitClose() throws InterruptedException {
        latch.await();
    }
}