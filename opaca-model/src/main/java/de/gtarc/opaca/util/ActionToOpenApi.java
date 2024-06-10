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

                // Create a map of names and type for the request body parameters
                var params = new HashMap<String, Object>();
                for (String name : action.getParameters().keySet()) {
                    params.put(name, Map.of("type", action.getParameters().get(name).getType()));
                }

                // The request body map holding the action parameters
                var requestBody = Map.of(
                        "content", Map.of(
                                "application/json", Map.of(
                                        "schema", Map.of(
                                                "type", "object",
                                                "properties", params // TODO check if this is OpenAPI conform
                                        )
                                )
                        ),
                        "required", true
                );

                // Responses that could be retrieved by calling an action
                var responses = Map.of(
                        "200", Map.of(
                                "description", "OK",
                                "content", Map.of(
                                        "*/*", Map.of(
                                                "schema", Map.of(
                                                        "type", action.getResult().getType(),
                                                        "items", action.getResult().getItems() == null ? Map.of() : action.getResult().getItems()
                                                )
                                        )
                                )
                        ),
                        "default", Map.of(
                                "description", "Unexpected error",
                                "content", Map.of(
                                        "application/json", Map.of(
                                                "schema", Map.of("$ref", "#/components/schemas/Error")
                                        )
                                )
                        )
                        // TODO Further error responses necessary?
                );

                // The path combines request body and responses
                // Includes the action description (if available), action name and query parameters
                var path = Map.of(
                        "post", Map.of(
                                "tags", List.of("agent"),
                                "description", action.getDescription() == null ? "" : action.getDescription(),
                                "operationId", action.getName(),
                                "parameters", List.of(
                                        Map.of("$ref", "#/components/parameters/timeoutParam"),
                                        Map.of("$ref", "#/components/parameters/containerIdParam"),
                                        Map.of("$ref", "#/components/parameters/forwardParam")
                                ),
                                "requestBody", requestBody,
                                "responses", responses
                        )
                );

                paths.put(String.format("/invoke/%s/%s", action.getName(), agent.getAgentId()), path);
            }
        }

        var root = Map.of(
            "openapi", "3.0.1",
                "info", Map.of(
                        "title", "Collection of actions provided by the agents running on the OPACA platform",
                        "version", "0.2"    // Same as in the opaca-api
                ),
                "paths", paths,
                "components", Map.of(
                    "schemas", Map.of(
                        "Error", Map.of(                // General error schema
                            "type", "object",
                            "properties", Map.of(
                                "code", Map.of(
                                    "type", "integer"
                                ),
                                "message", Map.of(
                                    "type", "string"
                                )
                            )
                        )
                    ),
                    "parameters", Map.of(               // Definition of query params
                        "timeoutParam", Map.of(
                            "name", "timeout",
                            "in", "query",
                            "description", "Timeout in seconds after which the action should abort",
                            "required", false,
                            "allowEmptyValue", true,
                            "schema", Map.of(
                                "type", "integer"
                            )
                        ),
                        "containerIdParam", Map.of(
                            "name", "containerId",
                            "in", "query",
                            "description", "Id of the container where the agent is running",
                            "required", false,
                            "allowEmptyValue", true,
                            "schema", Map.of(
                                "type", "string"
                            )
                        ),
                        "forwardParam", Map.of(
                            "name", "forward",
                            "in", "query",
                            "description", "Whether or not to include the connected runtime platforms",
                            "required", false,
                            "allowEmptyValue", true,
                            "schema", Map.of(
                                "type", "boolean"
                            )
                        )
                    ),
                "securitySchemes", Map.of(              // Definition for bearer authorization with JWT
                "bearerAuth", Map.of(
                    "type", "http",
                    "scheme", "bearer",
                    "bearerFormat", "JWT"
                    )
                )
            ),
            "security", List.of(
                Map.of("bearerAuth", List.of())
            )
        );

        return RestHelper.mapper.convertValue(root, JsonNode.class);
    }

}