package de.dailab.jiacpp.plattform;

import lombok.Data;
import java.sql.Timestamp;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogEntry {
    private String methodName;
    private Object[] inputParams;
    private Object result;
    private final Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    private final String uniqueId = UUID.randomUUID().toString();
    private String eventType;
    private String relatedId;
}
