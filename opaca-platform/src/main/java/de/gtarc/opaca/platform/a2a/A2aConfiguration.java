package de.gtarc.opaca.platform.a2a;

import de.gtarc.opaca.platform.PlatformConfig;
import de.gtarc.opaca.platform.auth.AuthUtils;
import de.gtarc.opaca.platform.services.LLMService;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
class A2aConfiguration {

    @Autowired
    private PlatformConfig config;

    @Autowired
    private LLMService llmService;

    @Autowired
    private AuthUtils authUtils;


    @Bean
    AgentCard agentCard() {
        String a2aBaseUrl = config.getOwnBaseUrl() + "/a2a";
        return AgentCard.builder()
                .name("OPACA Agent")
                .description("")
                .version("1.0.0")
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.HTTP_JSON.asString(), a2aBaseUrl)
                ))
                .preferredTransport(TransportProtocol.HTTP_JSON.asString())
                .securitySchemes(getSecuritySchemes())
                .securityRequirements(getSecurityRequirements())
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .defaultInputModes(Collections.singletonList("text"))
                .defaultOutputModes(Collections.singletonList("text"))
                .skills(Collections.singletonList(AgentSkill.builder()
                        .id("opaca_actions")
                        .name("OPACA Actions")
                        .description("""
                                 Allows to invoke Actions provided by Agents running in Agent Containers on this Platform.
                                 Exact capabilities depend on the containers that are currently started.
                                 """)
                        .tags(Collections.singletonList("opaca"))
                        .examples(List.of(
                                "Use the Home Assistant Agent to get the temperature in the kitchen.",
                                "Find a route by train from Berlin Ernst-Reuter-Platz to Hauptbahnhof."
                        ))
                        .build()))
                .build();
    }

    private Map<String, SecurityScheme> getSecuritySchemes() {
        if (authUtils.authEnabled()) {
            return Map.of(
                    "bearer", HTTPAuthSecurityScheme.builder().scheme("bearer").bearerFormat("JWT").build()
            );
        } else {
            return null;
        }
    }

    private List<SecurityRequirement> getSecurityRequirements() {
        if (authUtils.authEnabled()) {
            if (authUtils.authRequired()) {
                return List.of(
                        SecurityRequirement.builder().scheme("bearer", List.of()).build()
                );
            } else {
                return List.of(
                        SecurityRequirement.builder().scheme("bearer", List.of()).build(),
                        SecurityRequirement.builder().build()
                );
            }
        } else {
            return null;
        }
    }

    @Bean
    AgentExecutor agentExecutor() {
        return new AgentExecutor() {

            @Override
            public void execute(RequestContext context, AgentEmitter agentEmitter) throws A2AError {
                String userMessage = extractTextFromMessage(context.getMessage());
                String userName = extractUserNameFromContext(context.getCallContext());
                LLMService.QueryResponse response = llmService.query(userMessage, userName);
                agentEmitter.sendMessage(response.getResponse());
            }

            @Override
            public void cancel(RequestContext context, AgentEmitter agentEmitter) throws A2AError {
                throw new UnsupportedOperationError();
            }
        };
    }

    private String extractUserNameFromContext(ServerCallContext callContext) {
        var user = callContext == null ? null : callContext.getUser();
        return user != null && user.isAuthenticated()
                ? user.getUsername() : AuthUtils.ANONYMOUS_USER;
    }

    private String extractTextFromMessage(Message message) {
        if (message == null) {
            return "";
        }
        return message.parts().stream()
                .filter(TextPart.class::isInstance)
                .map(TextPart.class::cast)
                .map(TextPart::text)
                .collect(Collectors.joining("\n\n"));
    }
}
