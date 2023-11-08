package de.dailab.jiacpp.platform;

import de.dailab.jiacpp.model.Event;
import de.dailab.jiacpp.util.EventHistory;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
            String method = String.format("%s %s", httpRequest.getMethod(), httpRequest.getRequestURI());
            String sender = httpRequest.getHeader("senderId");
            Event callEvent = createCallEvent(method, sender);
            addEvent(callEvent);

            // process the request
            chain.doFilter(request, response);

            // create result or error event
            if (httpResponse.getStatus() >= 200 & httpResponse.getStatus() < 300 ) {
                addEvent(createResultEvent(callEvent));
            } else {
                addEvent(createErrorEvent(callEvent));
            }
        } else {
            // just process the request
            chain.doFilter(request, response);
        }
    }

    private boolean requestShouldCreateEvent(HttpServletRequest request) {
        Set<String> routes = Set.of(
                "/login", "/info", "/history",
                "/invoke", "/stream", "/send", "/broadcast",
                "/agents", "/containers", "/connections"
        );
        return routes.stream().anyMatch(r -> request.getRequestURI().startsWith(r))
                && ! request.getMethod().equals("GET");
    }


    private void addEvent(Event event) {
        EventHistory.getInstance().addEvent(event);
    }

    private Event createCallEvent(String method, String sender) {
        return new Event(Event.EventType.CALL, method, null, null);
    }

    private Event createResultEvent(Event related) {
        return new Event(Event.EventType.RESULT, null, related.getId(), null);
    }

    private Event createErrorEvent(Event related) {
        return new Event(Event.EventType.ERROR, null, related.getId(), null);
    }

}
