package de.dailab.jiacpp.model;

import lombok.Data;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This class provides the model for all events logged in our Logging History.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Event {

    /** name of the API method that was called */
    String methodName;

    /** input parameters of the API method */
    Object[] inputParams;

    /** result of the API call, if any */
    Object result;

    /** time when this event was created */
    final Long timestamp = System.currentTimeMillis();

    /** unique ID of this event */
    final String uniqueId = UUID.randomUUID().toString();

    String eventType;

    /** optional ID of a different event this event relates to */
    String relatedId;

}
