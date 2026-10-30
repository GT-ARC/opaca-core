package de.gtarc.opaca.platform;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import de.gtarc.opaca.model.AgentContainer;
import de.gtarc.opaca.model.AgentDescription;
import de.gtarc.opaca.model.Action;
import de.gtarc.opaca.platform.services.AgentsService;
import de.gtarc.opaca.platform.util.ActionToOpenApi;
import de.gtarc.opaca.platform.services.ContainersService;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.ObjectSchema;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller exposing the /mcp endpoints and hosting the MCP Sync Server (using JSON-RPC).
 *
 * In addition, also provides a convenience REST route /mcp to get a list of available MCP tools.
 */
@Log4j2
@Configuration
@RestController
public class McpController {

    private static final String SECURITY_CONTEXT = "securityContext";

    @Autowired
    private ContainersService containersService;

    @Autowired
    private AgentsService agentsService;

    @Autowired
    private JsonMapper objectMapper;

    private McpSyncServer mcpServer;
    private WebMvcStreamableServerTransportProvider transportProvider;

    private final Set<String> registeredTools = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        // Initialize WebMvcStreamableServerTransportProvider with JacksonMcpJsonMapper
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);
        transportProvider = WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .contextExtractor(request -> McpTransportContext.create(Map.of(
                    SECURITY_CONTEXT, SecurityContextHolder.getContext())))
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
    public void onContainerChanged(ContainersService.ContainerChangedEvent event) {
        log.info("Received container changed event, syncing tools.");
        syncTools();
    }

    @GetMapping("/mcp/list")
    @Operation(summary="Get list of available MCP tools (use JSON-RPC for calling MCP methods)", tags={"mcp"})
    public List<McpSchema.Tool> mcpTools() {
        return mcpServer.listTools();
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
                                    .callHandler((exchange, request) ->
                                            handleToolCall(exchange, agent.getAgentId(), action.getName(), request))
                                    .build();

                            toolsToAdd.add(toolSpec);
                        } catch (Exception e) {
                            log.error("Failed to build MCP tool specification for {}", toolName, e);
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
            log.info("Sync MCP Tools: registered {} tools.", registeredTools.size());
            mcpServer.notifyToolsListChanged();
        }
    }

    private McpSchema.CallToolResult handleToolCall(McpSyncServerExchange exchange, String agentId, String actionName,
            McpSchema.CallToolRequest request) {
        var args = request.arguments();
        log.info("MCP Tool execution request: agentId={}, actionName={}, arguments={}", agentId, actionName, args);
        // temporarily restore Spring security context, which is initially set, but "forgotten" by the time this runs;
        // the SecurityContext is thread-local, thus the change does not leak to a parallel REST call (tested manually)
        SecurityContext previousContext = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext((SecurityContext) exchange.transportContext().get(SECURITY_CONTEXT));
        try {
            // Invoke the existing agent execution logic directly
            Map<String, JsonNode> parameters = objectMapper.convertValue(args != null ? args : Map.of(), new TypeReference<>() {});
            JsonNode result = agentsService.invoke(actionName, parameters, agentId, -1, null, true);

            // Prevent double-quoting strings in TextContent
            String resultText;
            if (result == null) {
                resultText = "null";
            } else if (result.isString()) {
                resultText = result.asString();
            } else {
                resultText = objectMapper.writeValueAsString(result);
            }
            McpSchema.CallToolResult.Builder builder = McpSchema.CallToolResult.builder()
                .content(List.of(McpSchema.TextContent.builder(resultText).build()))
                .isError(false);

            // Only attach structuredContent if result is a valid JSON Object
            if (result != null && result.isObject()) {
                builder.structuredContent(result);
            }
            return builder.build();
        } catch (Exception e) {
            log.error("Error executing MCP tool {}", request.name(), e);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(McpSchema.TextContent.builder("Error invoking action: " + e.getMessage()).build()))
                    .isError(true)
                    .build();
        } finally {
            SecurityContextHolder.setContext(previousContext);
        }
    }

    private Map<String, Object> buildInputSchema(AgentContainer container, Action action) {
        ObjectSchema requestBodySchema = new ObjectSchema();
        List<String> requiredList = new ArrayList<>();
        for (var parameter : action.getParameters().entrySet()) {
            requestBodySchema.addProperty(
                    parameter.getKey(),
                    ActionToOpenApi.schemaFromParameter(parameter.getValue(), "#/definitions/", null));
            if (parameter.getValue().isRequired()) {
                requiredList.add(parameter.getKey());
            }
        }
        if (!requiredList.isEmpty()) {
            requestBodySchema.setRequired(requiredList);
        }

        // Convert the Swagger ObjectSchema to Map<String, Object>; swagger-core is still Jackson 2 based,
        // so this is the boundary: everything else (e.g. the definitions) is handled using Jackson 3
        @SuppressWarnings("unchecked")
        Map<String, Object> schemaMap = Json.mapper().convertValue(requestBodySchema, Map.class);

        // Inject custom schema definitions
        Map<String, Object> definitionsMap = new HashMap<>();
        for (var def : container.getImage().getDefinitions().entrySet()) {
            definitionsMap.put(def.getKey(), objectMapper.convertValue(def.getValue(), new TypeReference<Map<String, Object>>() {}));
        }
        for (var def : container.getImage().getDefinitionsByUrl().entrySet()) {
            definitionsMap.put(def.getKey(), Map.of("$ref", def.getValue()));
        }
        if (! definitionsMap.isEmpty()) {
            schemaMap.put("definitions", definitionsMap);
        }

        return schemaMap;
    }
}
