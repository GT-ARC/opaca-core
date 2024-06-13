package de.gtarc.opaca.util;

import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;

import de.gtarc.opaca.model.Action;
import de.gtarc.opaca.model.AgentDescription;
import de.gtarc.opaca.model.Parameter;

/**
 * ActionToOpenApi
 */
public class ActionToOpenApi {

    private static final Set<String> STANDARD_TYPES = new HashSet<>();

    // A list of standard openapi data types
    static {
        STANDARD_TYPES.add("string");
        STANDARD_TYPES.add("number");
        STANDARD_TYPES.add("integer");
        STANDARD_TYPES.add("boolean");
        STANDARD_TYPES.add("array");
        STANDARD_TYPES.add("object");
    }

    public static JsonNode createOpenApiSchema(List<AgentDescription> agents) {
        var paths = new HashMap<String, Object>();

        for (AgentDescription agent : agents) {
            for (Action action : agent.getActions()) {

                // Create a map of names and type for the request body parameters
                // If type is array, add additional items set
                var params = new HashMap<String, Object>();
                for (String name : action.getParameters().keySet()) {
                    var type = checkCustomType(action.getParameters().get(name).getType());
                    if (type == "array") {
                        params.put(name, Map.of("type", type, "items", processArrayItems(action.getParameters().get(name).getItems())));
                    }
                    else if (type instanceof Map) {
                        params.put(name, type);
                    }
                    else {
                        params.put(name, Map.of("type", type));
                    }
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

                // Check if action expects a result
                // If no result is expected, default response type is integer but nullable is set to true
                var schema = new HashMap<>();
                if (action.getResult() == null) {
                    schema.put("type", "integer");
                    schema.put("nullable", true);
                }
                // Otherwise, use type information from json schema of action
                else {
                    var type = checkCustomType(action.getResult().getType());
                    if (type instanceof Map) {
                        schema.putAll((Map<?, ?>) type);
                    }
                    else {
                        schema.put("type", type);
                    }
                    schema.put("nullable", false);
                    schema.put("items", action.getResult().getItems() == null ? Map.of() : processArrayItems(action.getResult().getItems()));
                }

                // Responses that could be retrieved by calling an action
                var responses = Map.of(
                        "200", Map.of(
                                "description", "OK",
                                "content", Map.of(
                                        "*/*", Map.of(
                                                "schema", schema
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
                                "operationId", agent.getAgentType() + "-" + action.getName(),   // Should be a unique string
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

    // If the type is part of the default data types, return type as string
    // Otherwise a reference to the custom type will be returned
    private static Object checkCustomType(String type) {
        return STANDARD_TYPES.contains(type) ? type : Map.of("$ref", "#/components/schemas/" + type);
    }

    // Recursively checks the contents of the ArrayItems type of the Parameter
    // If a default data type is used, returns ("type", <type>)
    // If a custom type is used, return a reference ("$ref", <reference-url>)
    private static Map<?, ?> processArrayItems(Parameter.ArrayItems arrayItems) {
        Object typeSchema = checkCustomType(arrayItems.getType());

        var result = new HashMap<>();

        // If type is array, recursively add items to array type
        if (arrayItems.getItems() != null) {
            result.put("type", typeSchema);
            result.put("items", processArrayItems(arrayItems.getItems()));
        }
        // If type is a map, it is a reference to a custom type, put into field "$ref"
        else if (typeSchema instanceof Map) {
            result.putAll((Map<?, ?>) typeSchema);
        }
        // If type is a default data type, put type description into field "type"
        else {
            result.put("type", typeSchema);
        }

        return result;
    }

}