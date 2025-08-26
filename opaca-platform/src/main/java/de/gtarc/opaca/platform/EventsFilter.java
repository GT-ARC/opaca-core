package de.gtarc.opaca.platform;

import de.gtarc.opaca.model.Event;
import de.gtarc.opaca.util.EventHistory;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Filter for pre- and postprocessing requests. Can be used for generating Events for the
 * History, for uniform logging, or for outright rejecting certain requests.
 */
@Service @NoArgsConstructor
public class EventsFilter implements Filter {

    @Autowired
    private WebSocketConfig webSocketHandler;

    @Override
    public void init(FilterConfig filterConfig) {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        if (request instanceof HttpServletRequest httpRequest &&
                response instanceof HttpServletResponse httpResponse &&
                requestShouldCreateEvent((HttpServletRequest) request)) {

            // create call event
            String route = String.format("%s %s", httpRequest.getMethod(), httpRequest.getRequestURI());
            String sender = httpRequest.getHeader(Event.HEADER_SENDER_ID);
            Event callEvent = createCallEvent(route, sender);
            addEvent(callEvent);

            // process the request
            chain.doFilter(request, response);

            // create result or error event
            if (httpResponse.getStatus() >= 200 & httpResponse.getStatus() < 300 ) {
                addEvent(createResultEvent(callEvent));
            } else {
                addEvent(createErrorEvent(callEvent, httpResponse.getStatus()));
            }
        } else {
            // just process the request
            chain.doFilter(request, response);
        }
    }

    private boolean requestShouldCreateEvent(HttpServletRequest request) {
        Map<String, Set<String>> routes = Map.of(
            "GET", Set.of("/stream", "/token"),
            "POST", Set.of("/users", "/stream", "/invoke", "/send", "/broadcast", "/login", "/containers", "/connections"),
            "PUT", Set.of("/users", "/containers"),
            "DELETE", Set.of("/users", "/containers", "/connections")
        );
        return routes.getOrDefault(request.getMethod(), Set.of()).stream()
                .anyMatch(r -> request.getRequestURI().startsWith(r));
    }

    private void addEvent(Event event) {
        EventHistory.getInstance().addEvent(event);
        if (event.getEventType() == Event.EventType.SUCCESS) {

            String[] routeParts = event.getRoute().split("\\s+");
            String firstPathSegment = "/" + routeParts[1].split("/")[1];
            webSocketHandler.broadcastEvent(firstPathSegment, event);
        }
    }

    private Event createCallEvent(String route, String sender) {
        return new Event(Event.EventType.CALL, route, sender, null, null, null);
    }

    private Event createResultEvent(Event related) {
        return new Event(Event.EventType.SUCCESS, related.getRoute(), related.getSenderId(), null, null, related.getId());
    }

    private Event createErrorEvent(Event related, int status) {
        return new Event(Event.EventType.ERROR, related.getRoute(), related.getSenderId(), null, status, related.getId());
    }

}
