package de.gtarc.opaca.platform;

import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import de.gtarc.opaca.model.Event;
import de.gtarc.opaca.util.RestHelper;

/**
 * Configuration and handler for the Websocket listening on "/subscribe". This websocket
 * can be used by clients to get updates about Events, i.e. different routes being called
 * on the platform, such as containers being added or removed, or actions being called.
 */
@Configuration
@EnableWebSocket
@Log4j2
public class WebSocketConfig implements WebSocketConfigurer {

    private final Map<WebSocketSession, String> sessionTopics = new ConcurrentHashMap<>();

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new WebSocketHandler(), "/subscribe").setAllowedOrigins("*");
    }

    /**
     * Regularly ping the client, and interpret any incoming text message and new topic to subscribe to
     */
    public class WebSocketHandler extends TextWebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            executorService.scheduleAtFixedRate(() -> send(session, new PingMessage()), 0, 10, TimeUnit.SECONDS);
            log.info("New WebSocket Connection established");
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            String topic = message.getPayload();
            sessionTopics.put(session, topic);
            log.info("New subscription for topic {}", topic);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
            sessionTopics.remove(session);
            log.info("Websocket connection closed");
        }
    }

    /**
     * Broadcast event to all clients subscribed to the given topic.
     */
    public void broadcastEvent(String topic, Event event) {
        log.debug("Broadcasting event to topic {}", topic);
        for (WebSocketSession session : sessionTopics.keySet()) {
            if (topic.equals(sessionTopics.get(session))) {
                try {
                    log.debug("Sending new message...");
                    send(session, new TextMessage(RestHelper.writeJson(event)));
                } catch (Exception e) {
                    log.warn("Error broadcasting message: {}", e.getMessage());
                }
            }
        }
    }

    private void send(WebSocketSession session, WebSocketMessage<?> message) {
        try {
            session.sendMessage(message);
        } catch (IOException e) {
            log.warn("Error sending message: {}", e.getMessage());
        }
    }

}
