package de.dailab.jiacpp.model;

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

    /** unique ID of this event */
    final String id = UUID.randomUUID().toString();

    /** time when this event was created */
    final Long timestamp = System.currentTimeMillis();

    /** to differentiate certain types of events */
    EventType eventType;

    /** name of the API method that was called */
    String methodName;

    /** optional ID of a different event this event relates to */
    String relatedId;

    /** the ID of the sending AgentContainer or RuntimePlatform, if set in the header */
    String senderId;

    /**
     * Nested EventType enum
     */
    public enum EventType {
        CALL,
        FORWARD,
        RESULT,
        ERROR
    }
}
