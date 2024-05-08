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

@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<WebSocketSession, String> sessionMessages = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String topic = message.getPayload();

        new Thread(() -> {
            while (sessions.contains(session)) {
                try {
                String response;
                switch (topic) {
                    case "topic_1":
                    response = "1";
                    break;
                    case "topic_2":
                    response = "2";
                    break;
                    case "topic_3":
                    response = "3";
                    break;
                    default:
                    response = "Unknown topic";
                }
                session.sendMessage(new TextMessage(response));
                TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                } catch (Exception e) {
                e.printStackTrace();
                }
            }
        }).start();
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        sessionMessages.remove(session);
    }
}