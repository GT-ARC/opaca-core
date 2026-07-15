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
                .capabilities(ServerCapabilities.builder()
                        .tools(true)
                        .build())
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
                            Map<String, Object> inputSchema = buildInputSchema(action);

                            McpSchema.Tool tool = McpSchema.Tool.builder(toolName, inputSchema)
                                    .description(action.getDescription() != null ? action.getDescription() : "")
                                    .build();

                            McpServerFeatures.SyncToolSpecification toolSpec = McpServerFeatures.SyncToolSpecification
                                    .builder()
                                    .tool(tool)
                                    .callHandler((exchange, request) -> handleToolCall(agent.getAgentId(),
                                            action.getName(), request))
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

    private Map<String, Object> buildInputSchema(Action action) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        if (action.getParameters() != null) {
            for (Map.Entry<String, Parameter> entry : action.getParameters().entrySet()) {
                String paramName = entry.getKey();
                Parameter param = entry.getValue();
                properties.put(paramName, convertParameterToSchema(param));
                if (param.isRequired()) {
                    required.add(paramName);
                }
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        return schema;
    }

    private Map<String, Object> convertParameterToSchema(Parameter parameter) {
        Map<String, Object> propSchema = new HashMap<>();
        if (parameter == null || "null".equals(parameter.getType())) {
            propSchema.put("type", "null");
            return propSchema;
        }

        switch (parameter.getType()) {
            case "string":
                propSchema.put("type", "string");
                break;
            case "number":
                propSchema.put("type", "number");
                break;
            case "integer":
                propSchema.put("type", "integer");
                break;
            case "boolean":
                propSchema.put("type", "boolean");
                break;
            case "object":
                propSchema.put("type", "object");
                break;
            case "array":
                propSchema.put("type", "array");
                if (parameter.getItems() != null) {
                    propSchema.put("items", convertArrayItemsToSchema(parameter.getItems()));
                }
                break;
            default:
                // Custom object reference, default to general object schema
                propSchema.put("type", "object");
                break;
        }

        if (parameter.getDefaultValue() != null) {
            propSchema.put("default", parameter.getDefaultValue());
        }

        return propSchema;
    }

    private Map<String, Object> convertArrayItemsToSchema(Parameter.ArrayItems items) {
        Map<String, Object> itemsSchema = new HashMap<>();
        switch (items.getType()) {
            case "string":
            case "number":
            case "integer":
            case "boolean":
            case "object":
                itemsSchema.put("type", items.getType());
                break;
            case "array":
                itemsSchema.put("type", "array");
                if (items.getItems() != null) {
                    itemsSchema.put("items", convertArrayItemsToSchema(items.getItems()));
                }
                break;
            default:
                itemsSchema.put("type", "object");
                break;
        }
        return itemsSchema;
    }
}
