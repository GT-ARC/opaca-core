package de.gtarc.opaca.platform.a2a;

import de.gtarc.opaca.platform.services.LLMService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.*;

import java.util.stream.Collectors;

@ApplicationScoped
public class AgentExecutorProducer {

    @Inject
    LLMService llmService;

    @Produces
    public AgentExecutor agentExecutor() {
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
                .collect(Collectors.joining());
    }
}
