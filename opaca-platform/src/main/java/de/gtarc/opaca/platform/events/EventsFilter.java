package de.gtarc.opaca.platform.events;

import de.gtarc.opaca.model.Event;
import de.gtarc.opaca.model.Event.*;
import de.gtarc.opaca.util.EventHistory;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Filter for pre- and postprocessing requests. Can be used for generating Events for the
 * History, for uniform logging, or for outright rejecting certain requests.
 */
@Service
public class EventsFilter implements Filter {

    @Autowired
    private EventsHandling eventsHandling;

    @Override
    public void init(FilterConfig filterConfig) {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        if (request instanceof HttpServletRequest httpRequest &&
                response instanceof HttpServletResponse httpResponse &&
                requestShouldCreateEvent(httpRequest)) {

            // create call event
            String route = String.format("%s %s", httpRequest.getMethod(), httpRequest.getRequestURI());
            String sender = httpRequest.getHeader(Event.HEADER_SENDER_ID);
            ApiEvent callEvent = createCallEvent(route, sender);
            eventsHandling.addEvent(callEvent);

            // process the request
            chain.doFilter(request, response);

            // create result or error event
            if (httpResponse.getStatus() >= 200 & httpResponse.getStatus() < 300 ) {
                eventsHandling.addEvent(createResultEvent(callEvent));
            } else {
                eventsHandling.addEvent(createErrorEvent(callEvent, httpResponse.getStatus()));
            }
        } else {
            // just process the request
            chain.doFilter(request, response);
        }
    }

    private boolean requestShouldCreateEvent(HttpServletRequest request) {
        Map<String, Set<String>> routes = Map.of(
            "GET", Set.of("/stream", "/token"),
            "POST", Set.of("/stream", "/invoke", "/send", "/broadcast", "/login", "/containers", "/connections"),
            "PUT", Set.of("/containers"),
            "DELETE", Set.of("/containers", "/connections")
        );
        return routes.getOrDefault(request.getMethod(), Set.of()).stream()
                .anyMatch(r -> request.getRequestURI().startsWith(r));
    }

    private ApiEvent createCallEvent(String route, String sender) {
        return new ApiEvent(ApiCallPhase.CALL, route, sender, null, null, null);
    }

    private ApiEvent createResultEvent(ApiEvent related) {
        return new ApiEvent(ApiCallPhase.SUCCESS, related.getRoute(), related.getSenderId(), null, null, related.getId());
    }

    private ApiEvent createErrorEvent(ApiEvent related, int status) {
        return new ApiEvent(ApiCallPhase.ERROR, related.getRoute(), related.getSenderId(), null, status, related.getId());
    }

}
