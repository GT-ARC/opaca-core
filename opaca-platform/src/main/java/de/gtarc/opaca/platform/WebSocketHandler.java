package de.gtarc.opaca.platform;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gtarc.opaca.model.Event;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private ConcurrentHashMap<WebSocketSession, String> sessionTopics = new ConcurrentHashMap<>();
    private ObjectMapper objectMapper = new ObjectMapper();
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        executorService.scheduleAtFixedRate(() -> sendPingToClient(session), 0, 10, TimeUnit.SECONDS);
        System.out.println("New WebSocket Connection established");
    }

    private void sendPingToClient(WebSocketSession session) {
        try {
            session.sendMessage(new PingMessage());
        } catch (IOException e) {
            System.out.println("Error sending ping to WebSocket client: " + e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String topic = message.getPayload();
        sessionTopics.put(session, topic);
        System.out.println("New Topic Consumption activated");
        System.out.println(topic);
    }

    public void broadcastEvent(String topic, Event message) {
        System.out.println("Broadcast new event ");
        System.out.println(topic);
        System.out.println(sessionTopics);
        for (WebSocketSession session : sessions) {
            if (topic.equals(sessionTopics.get(session))) {
                try {
                    System.out.println("Send new message");
                    String messageJson = objectMapper.writeValueAsString(message);
                    session.sendMessage(new TextMessage(messageJson));
                    System.out.println("Sent new message");
                } catch (Exception e) {
                    System.out.println("Error sending message: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        sessionTopics.remove(session);
        System.out.println("Websocket connection closed");
    }
}
