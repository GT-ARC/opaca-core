package de.gtarc.opaca.platform.services;

import com.fasterxml.jackson.databind.JsonNode;
import de.gtarc.opaca.platform.PlatformConfig;
import de.gtarc.opaca.util.RestHelper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashMap;
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

    private ChatModel model = null;

    private ChatMemory memory;

    @PostConstruct
    private void initialize() {
        try {
            this.model = OpenAiChatModel.builder()
                    .apiKey(config.llmApiKey)
                    .modelName(config.llmModelName)
                    //.strictTools(true) // problem with "generic" invoke tool
                    .logRequests(true)
                    .logResponses(true)
                    .build();
            this.memory = MessageWindowChatMemory.withMaxMessages(5); // for testing, later make user specific
            log.info("Successfully initialized Chat Model for " + config.llmModelName);
        } catch (Exception e) {
            log.warn("Could not initialize LLM Assistant", e);
        }
    }

    /**
     * TODO docs
     */
    public String query(String message) throws IOException {
        if (model == null) {
            throw new IllegalArgumentException("LLM Assistant not initialized");
        }
        var assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .systemMessage(getSystemPrompt())
                .tools(new AssistantTools())
                .maxToolCallingRoundTrips(3) // for testing, increase later
                .chatMemory(memory) // for testing, later make user specific
                .build();
        return assistant.chat(message);
        // TODO handle max iteration error
    }

    private String getSystemPrompt() throws IOException {
        var actionsJson = RestHelper.writeJson(agentsService.getAgents());
        return """
                You are the integrated LLM Assistant of the OPACA Runtime Platform. You have access to different
                'Agents', situated in 'Agent Containers', each providing different 'Actions'. You can call those
                actions with appropriate parameters given the "invoke" tool.
                
                Following is the full list of Agents and their actions, as JSON:
                
                %s
                
                Always use exactly the actions and parameters as provided in the list. If the list is empty, just
                tell the user that there are currently no actions. Never invent actions that are not there.
                """.formatted(actionsJson);
    }


    /**
     * Interface of the Assistant. In the simplest form, this is just String -> String, but it could also take a more
     * complex multi-parameter prompt template and return a complex, non-string result...
     */
    interface Assistant {
        String chat(String query);
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
