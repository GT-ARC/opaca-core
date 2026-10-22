package de.gtarc.opaca.platform.a2a;

import jakarta.servlet.http.HttpServletRequest;

import java.util.*;

import org.a2aproject.sdk.common.A2AHeaders;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.AuthenticatedUser;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.server.extensions.A2AExtensions;
import org.a2aproject.sdk.spec.A2AMethods;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.rest.context.RestContextKeys;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import de.gtarc.opaca.platform.auth.AuthUtils;


@RestController 
class A2aController {

    @Autowired
    private A2aConfiguration a2aConfig;

    @Autowired
    private RestHandler restHandler;

    @Autowired
    private AuthUtils authUtils;

    @Autowired
    private AgentCard agentCard;


    @GetMapping("/.well-known/agent-card.json")
    ResponseEntity<?> card() throws JsonProcessingException {
        var json = JsonUtil.toJson(agentCard);
        return ResponseEntity.ok(json);
    }

    @PostMapping(path="/a2a/message:send", consumes=MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> send(
            @RequestBody String body,
            HttpServletRequest request,
            Authentication authentication) {
        ServerCallContext context = createSendContext(request, authentication);
        var res = restHandler.sendMessage(context, null, body);
        return ResponseEntity.ok(res.toString());
    }

    private ServerCallContext createSendContext(HttpServletRequest request, Authentication authentication) {
        var userName = authUtils.getRequestUser();
        User user = userName.equals(AuthUtils.ANONYMOUS_USER)
                ? UnauthenticatedUser.INSTANCE : new AuthenticatedUser(authentication.getName());

        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            for (String name : Collections.list(headerNames)) {
                headers.put(name, request.getHeader(name));
            }
        }
        Map<String, Object> state = Map.of(
                RestContextKeys.HEADERS_KEY, headers,
                RestContextKeys.METHOD_NAME_KEY, A2AMethods.SEND_MESSAGE_METHOD,
                ServerCallContext.TRANSPORT_KEY, TransportProtocol.HTTP_JSON
        );

        List<String> extensionHeaders = Collections.list(request.getHeaders(A2AHeaders.A2A_EXTENSIONS));
        Set<String> requestedExtensions = A2AExtensions.getRequestedExtensions(extensionHeaders);
        return new ServerCallContext(user, state, requestedExtensions, request.getHeader(A2AHeaders.A2A_VERSION));
    }
}