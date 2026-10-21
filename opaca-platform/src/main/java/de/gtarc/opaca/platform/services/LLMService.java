package de.gtarc.opaca.platform.services;

import com.fasterxml.jackson.databind.JsonNode;
import de.gtarc.opaca.platform.PlatformConfig;
import de.gtarc.opaca.platform.auth.AuthUtils;
import de.gtarc.opaca.util.RestHelper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.Map;


/**
 * LLM Chat Assistant service for OPACA based on LangChain4J. The basic LLM-loop (tool selection, calling, evaluation
 * of the result and iteration) are handled by LangChain, as well as memory management. This is meant as a lightweight
 * LLM integration and no full-blown chat assistant like SAGE. There is one chat with a rolling/windowed chat history
 * per logged-in user (or for all anonymous users). The agent has access to the actions of all the agents running in
 * containers on this platform, but not to actions of connected platforms, or to the OPACA API itself (e.g. for adding
 * new containers).
 */
@Log4j2
@Service
public class LLMService {

    @Autowired
    private PlatformConfig config;

    @Autowired
    private AgentsService agentsService;

    @Autowired
    private AuthUtils authUtils;


    /** the LangChain chat assistant */
    private Assistant assistant = null;

    @PostConstruct
    private void initialize() {
        try {
            ChatModel model = OpenAiChatModel.builder()
                    .apiKey(config.llmApiKey)
                    .modelName(config.llmModelName)
                    .build();
            this.assistant = AiServices.builder(Assistant.class)
                    .chatModel(model)
                    .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(config.llmMemoryWindow))
                    .tools(new AssistantTools())
                    .maxToolCallingRoundTrips(config.llmMaxActionIterations)
                    .build();
            log.info("Successfully initialized Chat Assistant for model {}", config.llmModelName);
        } catch (Exception e) {
            log.warn("Could not initialize LLM Assistant", e);
        }
    }

    /**
     * Forward the user query to the LLM assistant. All the heavy lifting, like tool calls and evaluations, are done
     * by LangChain. Constructs and returns a complex result with a few additional information (like the called tools)
     * in addition to the actual LLM response.
     */
    public QueryResponse query(String message) throws IOException {
        if (assistant == null) {
            throw new IllegalArgumentException("LLM Assistant not initialized");
        }
        String userName = authUtils.getRequestUser();
        String actions = getActionsJson();
        Result<String> res = assistant.chat(userName, message, actions);
        return new QueryResponse(res.content(), res.toolExecutions().stream().map(t ->
                new QueryResponse.CalledAction(t.request().name(), t.request().arguments(), t.result())).toList());
        // TODO Response is a bit redundant, but Result is a bit much... what to include in the result exactly
        // TODO handle max iterations and other errors
    }

    private String getActionsJson() {
        try {
            return RestHelper.writeJson(agentsService.getAgents());
        } catch (IOException e) {
            return "(failed to fetch actions)";
        }
    }

    /**
     * Interface of the Assistant. In the simplest form, this is just String -> String, but it could also take a more
     * complex multi-parameter prompt template and return a complex, non-string result...
     */
    interface Assistant {

        @SystemMessage("""
                You are the integrated LLM Assistant of the OPACA Runtime Platform. You have access to different
                'Agents', situated in 'Agent Containers', each providing different 'Actions'. You can call those
                actions with appropriate parameters given the "invoke" tool.
                
                Following is the full list of Agents and their actions, as JSON:
                
                {{actionsJson}}
                
                Always use exactly the actions and parameters as provided in the list. If the list is empty, just
                tell the user that there are currently no actions. Never invent actions that are not there.
                """)
        Result<String> chat(
                @MemoryId String id,
                @UserMessage String query,
                @V("actionsJson") String actionsJson
        );

    }


    /**
     * The Tools the Assistant has access to. The tools are automatically executed by LangChain4J and results are
     * fed back into the LLM to serve as input for another tool, or to formulate the response. The Assistant builder
     * can take additional arguments for e.g. the max number of tools to call, or tool call pre- and post-processing.
     */
    class AssistantTools {

        @Tool
        Object invoke(String actionName, Map<String, JsonNode> parameters) throws IOException {
            log.info("LLM called Tool '{}' with {}", actionName, parameters);
            return agentsService.invoke(actionName, parameters, null, 10, null, false);
        }

    }


    /**
     * Wrapper for Query Response. This is still WIP and might change. Until then, it's put here,
     * later should be moved to model package, and the /query route be made "official API"...
     */
    @Data @AllArgsConstructor
    public static class QueryResponse {

        String response;

        List<CalledAction> actionInvocations;

        @Data @AllArgsConstructor
        static class CalledAction {

            String action;

            String parameters;

            Object result;
        }
    }

}
