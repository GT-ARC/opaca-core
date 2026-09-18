package de.gtarc.opaca.platform.events;

import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
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
import de.gtarc.opaca.model.Event.*;
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
        public void afterConnectionEstablished(@NotNull WebSocketSession session) {
            executorService.scheduleAtFixedRate(() -> send(session, new PingMessage()), 0, 10, TimeUnit.SECONDS);
            log.info("New WebSocket Connection established");
        }

        @Override
        protected void handleTextMessage(@NotNull WebSocketSession session, TextMessage message) {
            String topic = message.getPayload();
            sessionTopics.put(session, topic);
            log.info("New subscription for topic {}", topic);
        }

        @Override
        public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
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
            if (matches(sessionTopics.get(session), topic)) {
                try {
                    log.debug("Sending new message...");
                    send(session, new TextMessage(RestHelper.writeJson(event)));
                } catch (Exception e) {
                    log.warn("Error broadcasting message: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Topics are in form "api/firstRouteSegment", "platform/containers|connections", or "broadcast/topic".
     * Clients can subscribe in the same format, to an event type and optional suffix, e.g.
     * - "api/invoke" -> subscribe to all (successful) calls to "invoke"
     * - "platform/containers" -> subscribe to container started and stopped events
     * - "platform/" or "platform" -> subscribe to any platform events
     * - "broadcast/topic" -> subscribe to broadcast messages on channel 'topic'
     * - "broadcast/" or "broadcast" -> subscribe to any broadcast messages
     */
    private boolean matches(String subscribedTopic, String eventTopic) {
        var parts1 = subscribedTopic.split("/", 2);
        var parts2 = eventTopic.split("/", 2);
        if (parts1[0].equals(parts2[0])) {
            if (parts1.length == 1) return true;
            if (parts2.length == 2) return parts2[1].contains(parts1[1]);
        }
        return false;
    }

    private void send(WebSocketSession session, WebSocketMessage<?> message) {
        try {
            session.sendMessage(message);
        } catch (IOException e) {
            log.warn("Error sending message: {}", e.getMessage());
        }
    }

}
