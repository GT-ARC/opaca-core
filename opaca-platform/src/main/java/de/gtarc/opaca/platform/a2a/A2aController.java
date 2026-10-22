package de.gtarc.opaca.platform.a2a;

import jakarta.servlet.http.HttpServletRequest;

import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController 
class A2aController {

    @Autowired
    private A2aConfiguration a2aConfig;


    private RestHandler restHandler;

    @GetMapping("/.well-known/agent-card.json")
    ResponseEntity<?> card() throws JsonProcessingException {
        var json = JsonUtil.toJson(a2aConfig.agentCard());
        return ResponseEntity.ok(json);
    }

    @PostMapping(path="/a2a/message:send", consumes=MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> send(
            @RequestBody String body,
            HttpServletRequest request,
            Authentication authentication) {
        //ServerCallContext context = makeSdkContext(request, authentication);
        //return adapt(restHandler.sendMessage(context, null, body));
        return null;
    }
}