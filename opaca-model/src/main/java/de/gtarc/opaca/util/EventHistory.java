package de.gtarc.opaca.util;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import de.gtarc.opaca.model.Event;

/**
 * This class provides the Event History.
 */
public class EventHistory {
 
    public static int maxSize = 50;

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
            while (events.size() > maxSize) {
                events.removeFirst();
            }
        }
    }

    public List<Event> getEvents() {
        return List.copyOf(events);
    }
}


