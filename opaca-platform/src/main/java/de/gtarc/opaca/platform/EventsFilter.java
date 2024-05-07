package de.gtarc.opaca.platform;

import de.gtarc.opaca.model.Event;
import de.gtarc.opaca.util.EventHistory;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Set;

/**
 * Filter for pre- and postprocessing requests. Can be used for generating Events for the
 * History, for uniform logging, or for outright rejecting certain requests.
 *
 * TODO check how this interacts with Auth, and if it's called before or after the Auth Filter
 */
@Service @NoArgsConstructor
public class EventsFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        if (request instanceof HttpServletRequest &&
                response instanceof HttpServletResponse &&
                requestShouldCreateEvent((HttpServletRequest) request)) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

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
        Set<String> routes = Set.of(
                "/login", "/token",
                "/info", "/history", "/config",
                "/invoke", "/stream", "/send", "/broadcast",
                "/agents", "/containers", "/connections",
                "/users"
        );
        return routes.stream().anyMatch(r -> request.getRequestURI().startsWith(r))
                && ! request.getMethod().equals("GET");
    }


    private void addEvent(Event event) {
        EventHistory.getInstance().addEvent(event);
    }

    private Event createCallEvent(String route, String sender) {
        return new Event(Event.EventType.CALL, route, sender, null, null, null);
    }

    private Event createResultEvent(Event related) {
        return new Event(Event.EventType.SUCCESS, null, null, null, null, related.getId());
    }

    private Event createErrorEvent(Event related, int status) {
        return new Event(Event.EventType.ERROR, null, null, null, status, related.getId());
    }

}
