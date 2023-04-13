package de.dailab.jiacpp.util;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import de.dailab.jiacpp.model.Event;

// TODO
// 1. Truncating the history after a certain time, after a certain number of entries or similar

/**
 * This class provides the Event History.
 */

public class EventHistory {
    private static final EventHistory INSTANCE = new EventHistory();
    private final List<Event> events = Collections.synchronizedList(new LinkedList<>());

    private EventHistory() {
    }

    public static EventHistory getInstance() {
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


