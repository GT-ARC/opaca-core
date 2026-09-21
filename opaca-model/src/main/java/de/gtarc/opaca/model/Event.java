package de.gtarc.opaca.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.*;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This class provides the model for all events logged in our Events History.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(value = {
        @JsonSubTypes.Type(value=Event.ApiEvent.class, name="API"),
        @JsonSubTypes.Type(value=Event.ContainerEvent.class, name="CONTAINER"),
        @JsonSubTypes.Type(value=Event.ConnectionEvent.class, name="CONNECTION"),
        @JsonSubTypes.Type(value=Event.BroadcastEvent.class, name="BROADCAST"),
})
public abstract class Event {

    public enum EventType {
        API,
        CONTAINER,
        CONNECTION,
        BROADCAST
    }

    public enum ApiCallPhase {
        CALL,
        FORWARD,
        SUCCESS,
        ERROR
    }

    public enum PlatformEventType {
        ADDED,
        UPDATED,
        REMOVED
    }

    public static final String HEADER_SENDER_ID = "sender-id";


    /** unique ID of this event */
    final String id = UUID.randomUUID().toString();

    /** time when this event was created */
    final Long timestamp = System.currentTimeMillis();

    /** the type of event, determines the type of the payload */
    public abstract EventType getType();

    /** get short description what the event is about, including its type, e.g. "api/invoke" or "broadcast/xyz" */
    public abstract String slug();

    /**
     * Topics are in form "api/firstRouteSegment", "platform/containers|connections", or "broadcast/topic".
     * Clients can subscribe in the same format, to an event type and optional suffix, e.g.
     * - "api/invoke" -> subscribe to all (successful) calls to "invoke"
     * - "platform/containers" -> subscribe to container started and stopped events
     * - "platform/" or "platform" -> subscribe to any platform events
     * - "broadcast/topic" -> subscribe to broadcast messages on channel 'topic'
     * - "broadcast/" or "broadcast" -> subscribe to any broadcast messages
     */
    public boolean matches(String subscribedTopic) {
        var topic = subscribedTopic.split("/", 2);
        var slug = this.slug().split("/", 2);
        if (topic[0].equals(slug[0])) {
            if (topic.length == 1) return true;
            if (slug.length == 2) return slug[1].contains(topic[1]);
        }
        return false;
    }

    /**
     * whether the event should be broadcast to websocket at all, independent of the subscriber
     */
    public boolean shouldBroadcast() {
        return true;
    }


    @Data @AllArgsConstructor @NoArgsConstructor
    @EqualsAndHashCode(callSuper=true) @ToString(callSuper=true)
    public static class ApiEvent extends Event {
        final EventType type = EventType.API;

        /** phase of the API call */
        ApiCallPhase phase;

        /** method and route of the API, for CALL event */
        String route;

        /** the ID of the sending AgentContainer or RuntimePlatform, if set in the header, for CALL event */
        String senderId;

        /** receiver of forwarded call, for FORWARD event */
        String receiver;

        /** HTTP status code, for ERROR event */
        Integer statusCode;

        /** optional ID of a different event this event relates to */
        String relatedId;

        @Override
        public String slug() {
            String[] routeParts = route.split("\\s+");
            String firstPathSegment = routeParts[1].split("/")[1];
            return "api/" + firstPathSegment;
        }

        @Override
        public boolean shouldBroadcast() {
            return phase == ApiCallPhase.SUCCESS;
        }
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    @EqualsAndHashCode(callSuper=true) @ToString(callSuper=true)
    public static class ContainerEvent extends Event {
        final EventType type = EventType.CONTAINER;

        /** what happened on the platform */
        PlatformEventType event;

        /** for container-type events, the ID of the added or removed container */
        String containerId;

        /** for container-type events, the name of the image of the added or removed container */
        String imageName;

        @Override
        public String slug() {
            return "platform/" + type.name().toLowerCase();
        }
    }


    @Data @AllArgsConstructor @NoArgsConstructor
    @EqualsAndHashCode(callSuper=true) @ToString(callSuper=true)
    public static class ConnectionEvent extends Event {
        final EventType type = EventType.CONNECTION;

        /** what happened on the platform */
        PlatformEventType event;

        /** for connection-type events, the URL of the (dis)connected other Platform */
        String connectedUrl;

        @Override
        public String slug() {
            return "connection/" + type.name().toLowerCase();
        }
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    @EqualsAndHashCode(callSuper=true) @ToString(callSuper=true)
    public static class BroadcastEvent extends Event {
        final EventType type = EventType.BROADCAST;

        /** the topic or channel the message was broadcast to */
        String topic;

        /** the message sent in the broadcast */
        Message message;

        @Override
        public String slug() {
            return "broadcast/" + topic;
        }
    }
}
