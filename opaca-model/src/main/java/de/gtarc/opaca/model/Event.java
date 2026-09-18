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
        @JsonSubTypes.Type(value=Event.PlatformEvent.class, name="PLATFORM"),
        @JsonSubTypes.Type(value=Event.BroadcastEvent.class, name="BROADCAST"),
})
public abstract class Event {

    public enum EventType {
        API,
        PLATFORM,
        BROADCAST
    }

    public enum ApiCallPhase {
        CALL,
        FORWARD,
        SUCCESS,
        ERROR
    }

    public enum PlatformEventType {
        CONTAINER_ADDED,
        CONTAINER_REMOVED,
        CONNECTION_ADDED,
        CONNECTION_REMOVED
    }

    public static final String HEADER_SENDER_ID = "sender-id";


    /** unique ID of this event */
    final String id = UUID.randomUUID().toString();

    /** time when this event was created */
    final Long timestamp = System.currentTimeMillis();

    /** the type of event, determines the type of the payload */
    public abstract EventType getType();


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

    }

    @Data @AllArgsConstructor @NoArgsConstructor
    @EqualsAndHashCode(callSuper=true) @ToString(callSuper=true)
    public static class PlatformEvent extends Event {
        final EventType type = EventType.PLATFORM;

        /** what happened on the platform */
        PlatformEvent event;

        /** for container-type events, the ID of the added or removed container */
        String containerId;

        /** for container-type events, the name of the image of the added or removed container */
        String imageName;

        /** for connection-type events, the URL of the (dis)connected other Platform */
        String connectedUrl;

    }

    @Data @AllArgsConstructor @NoArgsConstructor
    @EqualsAndHashCode(callSuper=true) @ToString(callSuper=true)
    public static class BroadcastEvent extends Event {
        final EventType type = EventType.BROADCAST;

        /** the topic or channel the message was broadcast to */
        String topic;

        /** the message sent in the broadcast */
        Message message;

    }
}
