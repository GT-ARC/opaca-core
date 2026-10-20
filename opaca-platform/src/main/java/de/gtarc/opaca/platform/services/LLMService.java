package de.gtarc.opaca.platform.services;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Log4j2
@Service
public class LLMService {


    @Autowired
    private AgentsService agentsService;

    interface Assistant {
        String chat(String query);
    }

    static class AssistantTools {

        @Tool
        double add(double x, double y) {
            System.out.println("ADD CALLED");
            return x + y;
        }

    }


    public static void main(String[] args) {

        var apiKey = System.getenv("OPENAI_API_KEY");
        System.out.println("OPENAI_API_KEY: " + apiKey);

        ChatModel model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-4o-mini")
                .strictTools(true)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new AssistantTools())
                .build();

        System.out.println(assistant.chat("what tools do you have access to?"));
        System.out.println(assistant.chat("use a tool to add pi to 7"));
        System.out.println(assistant.chat("what was the last thing I asked you?"));

    }

}
