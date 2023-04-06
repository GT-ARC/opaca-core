package de.dailab.jiacpp.util;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import de.dailab.jiacpp.model.Event;

// TODO
// 1. Truncating the history after a certain time, after a certain number of entries or similar

/**
 * This class provides the Logging History.
 */

public class LoggingHistory {
    private static final LoggingHistory INSTANCE = new LoggingHistory();
    private final List<Event> events = Collections.synchronizedList(new LinkedList<>());

    private LoggingHistory() {
    }

    public static LoggingHistory getInstance() {
        return INSTANCE;
    }

    public void addEvent(Event entry) {
        if (entry != null) {
            events.add(entry);
        }
    }

    public List<Event> getEvents() {
        return events;
    }
}


