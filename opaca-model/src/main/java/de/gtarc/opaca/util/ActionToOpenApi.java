package de.gtarc.opaca.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import de.gtarc.opaca.model.Action;
import de.gtarc.opaca.model.AgentDescription;

/**
 * ActionToOpenApi
 */
public class ActionToOpenApi {

    public static JsonNode createOpenApiSchema(List<AgentDescription> agents) {
        var paths = new HashMap<String, Object>();

        for (AgentDescription agent : agents) {
            for (Action action : agent.getActions()) {
                var requestBody = Map.of(
                        "content", Map.of(
                                "application/json", Map.of(
                                        "schema", Map.of(
                                                "type", "object",
                                                "properties", action.getParameters() // TODO check if this is OpenAPI conform
                                        )
                                )
                        ),
                        "required", true
                );

                var responses = Map.of(
                        "200", Map.of(
                                "description", "OK",
                                "content", Map.of(
                                        "*/*", Map.of(
                                                "schema", action.getResult()
                                        )
                                )
                        ),
                        "default", Map.of(
                                "description", "Unexpected error",
                                "content", Map.of(
                                        "*/*", Map.of(
                                                "schema", "#/components/schemas/ErrorModel"
                                        )
                                )
                        )
                        // TODO Further error responses necessary?
                );

                var path = Map.of(
                        "post", Map.of(
                                "tags", List.of("agent"),
                                "description", action.getDescription() == null ? "" : action.getDescription(),
                                "operationId", action.getName(),
                                "parameters", List.of(
                                        Map.of(
                                                "name", "timeout",
                                                "in", "query",
                                                "description", "Timeout in seconds after which the action should abort",
                                                "required", false,
                                                "allowEmptyValue", true
                                        ),
                                        Map.of(
                                                "name", "containerId",
                                                "in", "query",
                                                "description", "Id of the container where the agent is running",
                                                "required", false,
                                                "allowEmptyValue", true
                                        ),
                                        Map.of(
                                                "name", "forward",
                                                "in", "query",
                                                "description", "Whether or not to include the connected runtime platforms",
                                                "required", false,
                                                "allowEmptyValue", true
                                        )
                                ),
                                "requestBody", requestBody,
                                "responses", responses,
                                "security", List.of(
                                        Map.of("bearerAuth", List.of())
                                )
                        )
                );

                paths.put(String.format("/invoke/%s/%s", action.getName(), agent.getAgentId()), path);
            }
        }

        var root = Map.of(
            "openapi", "3.1.0",
                "info", Map.of(), // TODO Maybe a combination of agent names and agent descriptions if available?
                "paths", paths,
                "components", Map.of(), // TODO what are some schemas and how to use them here?
                "security", List.of(
                    Map.of("bearerAuth", List.of())
                )
        );

        return RestHelper.mapper.convertValue(root, JsonNode.class);
    }

}