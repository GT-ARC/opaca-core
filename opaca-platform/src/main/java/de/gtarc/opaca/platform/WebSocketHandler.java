package de.gtarc.opaca.platform;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gtarc.opaca.model.Event;
import lombok.extern.java.Log;


@Component
@Log
public class WebSocketHandler extends TextWebSocketHandler {

    private CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private ConcurrentHashMap<WebSocketSession, String> sessionTopics = new ConcurrentHashMap<>();
    private ObjectMapper objectMapper = new ObjectMapper();
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        executorService.scheduleAtFixedRate(() -> send(session, new PingMessage()), 0, 10, TimeUnit.SECONDS);
        log.info("New WebSocket Connection established");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String topic = message.getPayload();
        sessionTopics.put(session, topic);
        log.info("New Topic Consumption activated for topic " + topic);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        sessionTopics.remove(session);
        log.info("Websocket connection closed");
    }

    private void send(WebSocketSession session, WebSocketMessage<?> message) {
        try {
            session.sendMessage(message);
        } catch (IOException e) {
            log.warning("Error sending message: " + e.getMessage());
        }
    }

    public void broadcastEvent(String topic, Event message) {
        log.fine("Broadcasting new event to topic " + topic);
        for (WebSocketSession session : sessions) {
            if (topic.equals(sessionTopics.get(session))) {
                try {
                    log.fine("Sending new message...");
                    String messageJson = objectMapper.writeValueAsString(message);
                    send(session, new TextMessage(messageJson));
                } catch (Exception e) {
                    log.warning("Error sending message: " + e.getMessage());
                }
            }
        }
    }
}
