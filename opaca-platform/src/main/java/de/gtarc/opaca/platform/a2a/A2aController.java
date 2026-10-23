package de.gtarc.opaca.platform.a2a;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j2;

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
import org.a2aproject.sdk.spec.Message;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import de.gtarc.opaca.platform.auth.AuthUtils;


@Log4j2 
@RestController
@SecurityRequirement(name = "bearerAuth")
class A2aController {

    @Autowired
    private RestHandler restHandler;

    @Autowired
    private AuthUtils authUtils;

    @Autowired
    private AgentCard agentCard;


    @GetMapping("/.well-known/agent-card.json")
    ResponseEntity<?> card() throws JsonProcessingException {
        var json = JsonUtil.toJson(agentCard);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    }

    @PostMapping(path="/a2a/message:send", consumes=MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> send(
            @RequestBody String body,
            @RequestHeader(name = A2AHeaders.A2A_VERSION, required = false) String a2aVersion,
            HttpServletRequest request) throws JsonProcessingException {
        /*
        {
          "message": {
            "messageId": "test-message-001",
            "role": "ROLE_USER",
            "parts": [
              {
                "text": "Hello! What actions can you perform?"
              }
            ]
          }
        }
         */
        ServerCallContext context = createSendContext(request);
        String user = authUtils.getRequestUser();
        var res = restHandler.sendMessage(context, user, body);
        return ResponseEntity.status(res.getStatusCode())
                .contentType(MediaType.parseMediaType(res.getContentType()))
                .headers(headers -> res.getHeaders().forEach(headers::add))
                .body(res.getBody());
    }

    private ServerCallContext createSendContext(HttpServletRequest request) {
        var userName = authUtils.getRequestUser();
        System.out.println("username: " + userName);
        User user = userName.equals(AuthUtils.ANONYMOUS_USER)
                ? UnauthenticatedUser.INSTANCE : new AuthenticatedUser(userName);

        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            for (String name : Collections.list(headerNames)) {
                System.out.println("HEADER " + name + ": " + request.getHeader(name));
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