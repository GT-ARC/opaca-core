package de.gtarc.opaca.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import de.gtarc.opaca.model.Action;
import de.gtarc.opaca.model.AgentDescription;
import de.gtarc.opaca.model.Parameter;
import de.gtarc.opaca.model.Parameter.ArrayItems;

/**
 * ActionToOpenApi
 */
public class ActionToOpenApi {

    public static void main(String[] args) {
        var agents = List.of(
            new AgentDescription("agentA", "typeA", List.of(
                new Action("foo", Map.of(
                    "param1", new Parameter("string"),
                    "param2", new Parameter("int", false),
                    "param3", new Parameter("array", true, new ArrayItems("Car", null))
                ), new Parameter("string"))
            ), List.of())
        );
        var openApi = createOpenApi(agents);
        System.out.println(openApi);
    }
    
    public static JsonNode createOpenApi(List<AgentDescription> agents) {
        var paths = new HashMap<String, Object>();

        for (AgentDescription agent : agents) {
            for (Action action : agent.getActions()) {

                var requestBody = Map.of(
                    "content", Map.of(
                        "application/json", Map.of(
                            "schema", Map.of(
                                "type", "object",
                                "properties", action.getParameters()
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
                    )
                    // TODO error responses necessary?
                );

                var path = Map.of(
                    "post", Map.of(
                        "summary", "", // TODO ???
                        "operationId", "", // TODO ???
                        "parameters", List.of(), // TODO query parameters for timeout, containerId, forward?
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

        var components = Map.of(); // TODO JSON Schema definitions used in action params and results

        var root = Map.of(
            "openapi", "3.0.1",
            "info", Map.of(), // TODO same as for OPACA API?
            "servers", Map.of(), // TODO same as for OPACA API?
            "paths", paths,
            "components", components, 
            "securitySchemes", Map.of() // TODO same as for OPACA API?
        );
        return RestHelper.mapper.convertValue(root, JsonNode.class);
    }

}