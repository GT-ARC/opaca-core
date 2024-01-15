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

    /** unique ID of this event */
    final String id = UUID.randomUUID().toString();

    /** time when this event was created */
    final Long timestamp = System.currentTimeMillis();

    /** to differentiate certain types of events */
    EventType eventType;

    /** name of the API method that was called */
    String methodName;

    /** input parameters of the API method */
    Object[] inputParams;

    /** result of the API call, if any */
    Object result;

    /** optional ID of a different event this event relates to */
    String relatedId;

    /**
     * Nested EventType enum
     */
    public enum EventType {
        API_CALL,
        API_RESULT,
        API_ERROR
    }
}
