package de.gtarc.opaca.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.NoArgsConstructor;

/**
 * This class provides the model for all events logged in our Logging History.
 */
@Data @AllArgsConstructor @NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Event {

    public static final String HEADER_SENDER_ID = "sender-id";


    /** unique ID of this event */
    final String id = UUID.randomUUID().toString();

    /** time when this event was created */
    final Long timestamp = System.currentTimeMillis();

    /** to differentiate certain types of events */
    EventType eventType;

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


    /**
     * Nested EventType enum
     */
    public enum EventType {
        CALL,
        FORWARD,
        SUCCESS,
        ERROR
    }
}
