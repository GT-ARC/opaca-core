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
 * TODO docs
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
                    //.strictJsonSchema(true) // for response schema including tool calls
                    //.strictTools(true) // problem with "generic" invoke tool
                    //.logRequests(true)
                    //.logResponses(true)
                    .build();
            this.assistant = AiServices.builder(Assistant.class)
                    .chatModel(model)
                    .systemMessageProvider(id -> getSystemPrompt())
                    // make parameter? includes both sides, also intermediate?
                    .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(10))
                    .tools(new AssistantTools())
                    .maxToolCallingRoundTrips(3) // for testing, increase later
                    .build();
            log.info("Successfully initialized Chat Assistant for model " + config.llmModelName);
        } catch (Exception e) {
            log.warn("Could not initialize LLM Assistant", e);
        }
    }

    /**
     * TODO docs
     */
    public Object query(String message) throws IOException {
        if (assistant == null) {
            throw new IllegalArgumentException("LLM Assistant not initialized");
        }
        String userName = authUtils.getRequestUser();
        System.out.println("USER " + userName);
        Result<String> res = assistant.chat(userName, message);
        System.out.println(res);

        return new Response(res.content(), res.toolExecutions().stream().map(t ->
                new Response.CalledAction(t.request().name(), t.request().arguments(), t.result())).toList());
        // TODO Response is a bit redundant, but Result is a bit much... what to include in the result exactly
        // TODO handle max iterations and other errors
    }

    private String getSystemPrompt() {
        return """
                You are the integrated LLM Assistant of the OPACA Runtime Platform. You have access to different
                'Agents', situated in 'Agent Containers', each providing different 'Actions'. You can call those
                actions with appropriate parameters given the "invoke" tool.
                
                Following is the full list of Agents and their actions, as JSON:
                
                %s
                
                Always use exactly the actions and parameters as provided in the list. If the list is empty, just
                tell the user that there are currently no actions. Never invent actions that are not there.
                """.formatted(getActionsJson());
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

        Result<String> chat(@MemoryId String id, @UserMessage String query);

    }


    @Data @AllArgsConstructor
    static class Response {
        String response;
        List<CalledAction> actionInvocations;

        @Data @AllArgsConstructor
        static class CalledAction {
            String action;
            String parameters;
            Object result;
        }
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

}
