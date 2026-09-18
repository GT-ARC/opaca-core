package de.gtarc.opaca.platform.events;

import de.gtarc.opaca.model.Event.*;
import de.gtarc.opaca.platform.services.ConnectionsService;
import de.gtarc.opaca.platform.services.ContainersService;
import de.gtarc.opaca.util.EventHistory;
import jakarta.servlet.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Formerly part of EventsFilter, now promoted to own class, to also be used for Broadcast and Platform Events.
 * Adds events to the EventHistory and creates Websocket Broadcast message, depending on event type.
 */
@Service
public class EventsHandling {

    @Autowired
    private WebSocketConfig webSocketHandler;

    private final EventHistory eventHistory = EventHistory.getInstance();

    public void addEvent(ApiEvent event) {
        eventHistory.addEvent(event);
        if (event.getPhase() == ApiCallPhase.SUCCESS) {
            String[] routeParts = event.getRoute().split("\\s+");
            String firstPathSegment = routeParts[1].split("/")[1];
            webSocketHandler.broadcastEvent("api/" + firstPathSegment, event);
        }
    }

    public void addEvent(PlatformEvent event) {
        eventHistory.addEvent(event);
        webSocketHandler.broadcastEvent("platform/" + event.getType().name().toLowerCase(), event);
    }

    public void addEvent(BroadcastEvent event) {
        eventHistory.addEvent(event);
        webSocketHandler.broadcastEvent("broadcast/" + event.getTopic(), event);
    }


    @EventListener
    public void onContainerChanged(ContainersService.ContainerChangedEvent event) {
        addEvent(event.getEvent());
    }

    @EventListener
    public void onConnectionChanged(ConnectionsService.ConnectionChangedEvent event) {
        addEvent(event.getEvent());
    }

}
