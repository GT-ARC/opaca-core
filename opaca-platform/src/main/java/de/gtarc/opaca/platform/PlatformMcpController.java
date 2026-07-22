package de.gtarc.opaca.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gtarc.opaca.model.AgentContainer;
import de.gtarc.opaca.model.AgentDescription;
import de.gtarc.opaca.model.Action;
import de.gtarc.opaca.model.Parameter;
import de.gtarc.opaca.platform.services.AgentsService;
import de.gtarc.opaca.platform.services.ContainersService;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import de.gtarc.opaca.platform.event.ContainerChangedEvent;
import de.gtarc.opaca.platform.util.ActionToOpenApi;
import io.swagger.v3.oas.models.media.ObjectSchema;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller exposing the /mcp endpoints and hosting the MCP Sync Server.
 */
@Log4j2
@Configuration
public class PlatformMcpController {

    @Autowired
    private ContainersService containersService;

    @Autowired
    private AgentsService agentsService;

    @Autowired
    private ObjectMapper objectMapper;

    private McpSyncServer mcpServer;
    private WebMvcStreamableServerTransportProvider transportProvider;

    private final Set<String> registeredTools = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        // Initialize WebMvcStreamableServerTransportProvider with JacksonMcpJsonMapper
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);
        transportProvider = WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint("/mcp")
                .build();

        // Initialize MCP Sync Server
        mcpServer = McpServer.sync(transportProvider)
                .serverInfo("opaca-mcp-platform", "0.5")
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .build();

        log.info("Initialized PlatformMcpController with WebMvcStreamableServerTransportProvider.");
    }

    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction() {
        return transportProvider.getRouterFunction();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application ready, performing initial tool synchronization...");
        syncTools();
    }

    @EventListener
    public void onContainerChanged(ContainerChangedEvent event) {
        log.info("Received container changed event, syncing tools: {}", event);
        syncTools();
    }

    private synchronized void syncTools() {
        Set<String> activeTools = new HashSet<>();
        List<McpServerFeatures.SyncToolSpecification> toolsToAdd = new ArrayList<>();

        for (AgentContainer container : containersService.getContainers()) {
            for (AgentDescription agent : container.getAgents()) {
                for (Action action : agent.getActions()) {
                    String toolName = agent.getAgentId() + "__" + action.getName();
                    activeTools.add(toolName);

                    if (!registeredTools.contains(toolName)) {
                        try {
                            Map<String, Object> inputSchema = buildInputSchema(container, action);

                            McpSchema.Tool tool = McpSchema.Tool.builder(toolName, inputSchema)
                                    .description(action.getDescription() != null ? action.getDescription() : "")
                                    .build();

                            McpServerFeatures.SyncToolSpecification toolSpec = McpServerFeatures.SyncToolSpecification
                                    .builder()
                                    .tool(tool)
                                    .callHandler((exchange, request) -> handleToolCall(agent.getAgentId(), action.getName(), request))
                                    .build();

                            toolsToAdd.add(toolSpec);
                        } catch (Exception e) {
                            log.error("Failed to build MCP tool specification for: " + toolName, e);
                        }
                    }
                }
            }
        }

        boolean changed = false;

        // Register new tools
        for (McpServerFeatures.SyncToolSpecification spec : toolsToAdd) {
            mcpServer.addTool(spec);
            registeredTools.add(spec.tool().name());
            changed = true;
        }

        // Remove stale tools
        Iterator<String> it = registeredTools.iterator();
        while (it.hasNext()) {
            String toolName = it.next();
            if (!activeTools.contains(toolName)) {
                mcpServer.removeTool(toolName);
                it.remove();
                changed = true;
            }
        }

        if (changed) {
            log.info("Sync MCP Tools: registered " + registeredTools.size() + " tools. Active: " + registeredTools);
            mcpServer.notifyToolsListChanged();
        }
    }

    private McpSchema.CallToolResult handleToolCall(String agentId, String actionName,
            McpSchema.CallToolRequest request) {
        log.info("MCP Tool execution request: agentId={}, actionName={}, arguments={}", agentId, actionName,
                request.arguments());
        try {
            Map<String, JsonNode> parameters = objectMapper.convertValue(
                    request.arguments() != null ? request.arguments() : Map.of(),
                    new TypeReference<Map<String, JsonNode>>() {
                    });

            // Invoke the existing agent execution logic directly
            JsonNode result = agentsService.invoke(actionName, parameters, agentId, -1, null, true);

            String resultText = result != null ? objectMapper.writeValueAsString(result) : "null";
            return McpSchema.CallToolResult.builder()
                    .content(List.of(McpSchema.TextContent.builder(resultText).build()))
                    .isError(false)
                    .build();

        } catch (Exception e) {
            log.error("Error executing MCP tool: " + request.name(), e);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(McpSchema.TextContent.builder("Error invoking action: " + e.getMessage()).build()))
                    .isError(true)
                    .build();
        }
    }

    private Map<String, Object> buildInputSchema(AgentContainer container, Action action) {
        ObjectSchema requestBodySchema = new ObjectSchema();
        List<String> requiredList = new ArrayList<>();
        if (action.getParameters() != null) {
            for (var parameter : action.getParameters().entrySet()) {
                requestBodySchema.addProperty(
                        parameter.getKey(),
                        ActionToOpenApi.schemaFromParameter(parameter.getValue(), "#/definitions/"));
                if (parameter.getValue().isRequired()) {
                    requiredList.add(parameter.getKey());
                }
            }
        }
        if (!requiredList.isEmpty()) {
            requestBodySchema.setRequired(requiredList);
        }

        // Convert the Swagger ObjectSchema to Map<String, Object>
        Map<String, Object> schemaMap = io.swagger.v3.core.util.Json.mapper().convertValue(
                requestBodySchema,
                new TypeReference<Map<String, Object>>() {
                });

        // Inject custom schema definitions
        Map<String, Object> definitionsMap = new HashMap<>();

        // Add standard custom definitions defined inside image
        definitionsMap.putAll(container.getImage().getDefinitions());

        // Add custom definitions by URL as $ref schemas
        for (var definition : container.getImage().getDefinitionsByUrl().entrySet()) {
            definitionsMap.put(definition.getKey(), Map.of("$ref", definition.getValue()));
        }

        if (!definitionsMap.isEmpty()) {
            schemaMap.put("definitions", definitionsMap);
        }

        return schemaMap;
    }
}
