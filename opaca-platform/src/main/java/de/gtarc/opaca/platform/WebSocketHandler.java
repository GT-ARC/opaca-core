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
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gtarc.opaca.model.Event;
import jakarta.annotation.PostConstruct;
@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private ConcurrentHashMap<WebSocketSession, String> sessionTopics = new ConcurrentHashMap<>();
    private ObjectMapper objectMapper = new ObjectMapper();
    

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String topic = message.getPayload();
        sessionTopics.put(session, topic);
    }

    public void broadcastEvent(String topic, Event message) {
        for (WebSocketSession session : sessions) {
            if (topic.equals(sessionTopics.get(session))) {
                try {
                    String messageJson = objectMapper.writeValueAsString(message);
                    session.sendMessage(new TextMessage(messageJson));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        sessionTopics.remove(session);
    }
}