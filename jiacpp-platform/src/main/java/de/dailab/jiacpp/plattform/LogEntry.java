package de.dailab.jiacpp.plattform;

import lombok.Data;
import java.sql.Timestamp;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This class provides the model of log entries for our Logging History.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogEntry {
    private String methodName;
    private Object[] inputParams;
    private Object result;
    private final Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    private final String uniqueId = UUID.randomUUID().toString(); // ID to mkae every entry unique
    private String eventType;
    private String relatedId; // ID to map events that relate to each other
}
