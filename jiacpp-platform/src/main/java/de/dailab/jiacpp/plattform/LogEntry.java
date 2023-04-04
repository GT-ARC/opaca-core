package de.dailab.jiacpp.plattform;

import lombok.Data;

@Data
public class LogEntry {
    private String methodName;
    private Object[] inputParams;
    private Object result;
    private boolean success;
    private String errorMessage;
}
