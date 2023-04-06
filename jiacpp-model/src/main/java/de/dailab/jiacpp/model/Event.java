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
    private String methodName;
    private Object[] inputParams;
    private Object result;
    private final long timestamp = System.currentTimeMillis();
    private final String uniqueId = UUID.randomUUID().toString(); // ID to make every entry unique
    private String eventType;
    private String relatedId; // ID to map events that relate to each other
}
