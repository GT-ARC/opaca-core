package de.gtarc.opaca.platform.a2a;

import de.gtarc.opaca.platform.PlatformConfig;
import de.gtarc.opaca.platform.services.LLMService;
import org.a2aproject.sdk.server.AgentCardCacheMetadata;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.server.auth.TaskOperation;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.server.config.A2AConfigProvider;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.events.QueueManager;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.spec.*;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Configuration
class A2aConfiguration {

    @Autowired
    private PlatformConfig config;

    @Autowired
    private LLMService llmService;


    @Bean
    AgentCard agentCard() {
        String a2aBaseUrl = config.getOwnBaseUrl() + "/a2a";
        return AgentCard.builder()
                .name("OPACA Agent")
                .description("")
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.HTTP_JSON.asString(), a2aBaseUrl)
                ))
                .version("1.0.0")
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

    @Bean
    AgentExecutor agentExecutor() {
        return new AgentExecutor() {

            @Override
            public void execute(RequestContext context, AgentEmitter agentEmitter) throws A2AError {
                String userMessage = extractTextFromMessage(context.getMessage());
                LLMService.QueryResponse response = llmService.query(userMessage);
                agentEmitter.sendMessage(response.getResponse());
            }

            @Override
            public void cancel(RequestContext context, AgentEmitter agentEmitter) throws A2AError {
                throw new UnsupportedOperationError();
            }
        };
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
