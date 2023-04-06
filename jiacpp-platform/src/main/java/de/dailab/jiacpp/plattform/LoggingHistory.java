package de.dailab.jiacpp.plattform;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

// TODO
// 1. Truncating the history after a certain time, after a certain number of entries or similar

/**
 * This class provides the Logging History.
 */

public class LoggingHistory {
    private static final LoggingHistory INSTANCE = new LoggingHistory();
    private final List<LogEntry> logEntries = Collections.synchronizedList(new LinkedList<>());

    private LoggingHistory() {
    }

    public static LoggingHistory getInstance() {
        return INSTANCE;
    }

    public void addLogEntry(LogEntry entry) {
        if (entry != null) {
            logEntries.add(entry);
        }
    }

    public List<LogEntry> getLogEntries() {
        return logEntries;
    }
}


