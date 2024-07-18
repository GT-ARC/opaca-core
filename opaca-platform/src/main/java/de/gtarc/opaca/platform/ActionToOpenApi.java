package de.gtarc.opaca.platform;

import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.gtarc.opaca.model.*;
import de.gtarc.opaca.model.Parameter.ArrayItems;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Creates an Open-API compliant specification of all the Actions provided by the Agents on this platform,
 * that can be called using the /invoke route. This is complementary to the /api-docs route provided by
 * Swagger itself, which can only provide Open-API specifications for all the "static" services that are
 * part of the OPACA API, but not for the "dynamic" actions that may come and go at runtime.
 */
public class ActionToOpenApi {

    private final static ObjectMapper mapper = new ObjectMapper();

    /**
     * Create Open-API spec in JSON or YAML format for the actions in the given Agent Containers. This method
     * can provide an Open-API spec for all containers on the platform or for containers for this and connected
     * platforms, but to stay consistent with other similar OPACA routes like /agents or /info, only the containers
     * running on the platform itself should be passed.
     * 
     * @param agentsContainers List of agent containers currently running on this platform
     * @param format Whether to return the spec in JSON or YAML format
     * @param enableAuth Indicates if platform has authentication enabled
     * @return
     */
    public static String createOpenApiSchema(Collection<AgentContainer> agentsContainers, ActionFormat format,
                                             Boolean enableAuth) {
        // Check for custom definitions in agent container images and add to openapi components
        // Also check for external definitions by url
        Components components = new Components();
        for (AgentContainerImage images : agentsContainers.stream().map(AgentContainer::getImage).toList()) {
            for (var definition : images.getDefinitions().entrySet()) {
                Schema<?> schema = mapper.convertValue(definition.getValue(), Schema.class);
                components.addSchemas(definition.getKey(), schema);
            }
            for (var definition : images.getDefinitionsByUrl().entrySet()) {
                Schema<?> schema = new Schema<>().$ref(definition.getValue());
                components.addSchemas(definition.getKey(), schema);
            }
        }

        // Only add query parameters and errors if any action is available
        if (!agentsContainers.isEmpty()) {
            // Add standard query parameters to components
            components.addParameters("timeoutParam", makeQueryParam("timeout",
                    "Timeout in seconds after which the action should abort", new IntegerSchema()));
            components.addParameters("containerIdParam", makeQueryParam("containerId",
                    "Id of the container on which the agent is running", new StringSchema()));
            components.addParameters("forwardParam", makeQueryParam("forward",
                    "Whether or not to include the connected runtime platforms", new BooleanSchema()));

            // Add Error schema to components
            components.addSchemas("Error", new ObjectSchema()
                    .addProperty("code", new IntegerSchema())
                    .addProperty("message", new StringSchema()));
        }

        // Only create security component if auth is enabled
        if (enableAuth) {
            // Add security scheme
            components.addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT"));
        }
        // Create Paths
        Paths paths = new Paths();

        // Loop through each container and agent to add each action to the openapi spec
        for (var container : agentsContainers) {
            for (var agent : container.getAgents()) {
                for (Action action : agent.getActions()) {

                    // Request Body
                    Schema<?> requestBodySchema = new ObjectSchema();
                    List<String> requiredList = new ArrayList<>();
                    for (var parameter : action.getParameters().entrySet()) {
                        requestBodySchema.addProperty(parameter.getKey(), schemaFromParameter(parameter.getValue()));
                        if (parameter.getValue().getRequired()) {
                            requiredList.add(parameter.getKey());
                        }
                    }
                    requestBodySchema.setRequired(requiredList);
                    RequestBody requestBody = new RequestBody()
                            .content(new Content().addMediaType("application/json", new MediaType().schema(requestBodySchema)))
                            .required(true);

                    // Responses
                    ApiResponse response200 = new ApiResponse()
                            .description("OK")
                            .content(new Content().addMediaType("*/*", new MediaType().schema(schemaFromParameter(action.getResult()))));
                    ApiResponse responseDefault = new ApiResponse()
                            .description("Unexpected error")
                            .content(new Content().addMediaType("application/json", new MediaType().schema(new Schema<>().$ref("#/components/schemas/Error"))));

                    // Path Item
                    PathItem pathItem = new PathItem().post(new Operation()
                            .requestBody(requestBody)
                            .responses(new ApiResponses().addApiResponse("200", response200).addApiResponse("default", responseDefault))
                            .description(action.getDescription())
                            .operationId(container.getContainerId() + ";" + agent.getAgentId() + ";" + action.getName())
                            .addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter().$ref("#/components/parameters/timeoutParam"))
                            .addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter().$ref("#/components/parameters/containerIdParam"))
                            .addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter().$ref("#/components/parameters/forwardParam")));
                    paths.addPathItem(String.format("/invoke/%s/%s", action.getName(), agent.getAgentId()), pathItem);
                }
            }
        }

        // Merge everything together
        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("Collection of actions provided by the agents running on the OPACA platform")
                        .version("0.2"))
                .paths(paths)
                .components(components);

        // Only add security requirement if auth is enabled
        if (enableAuth) {
            openAPI.security(List.of(new SecurityRequirement().addList("bearerAuth")));
        }

        return switch (format) {
            case JSON -> Json.pretty(openAPI);
            case YAML -> Yaml.pretty(openAPI);
        };
    }

    private static Schema<?> schemaFromParameter(Parameter parameter) {
        if (parameter == null || parameter.getType().equals("null")) {
            return new ObjectSchema().nullable(true);
        }

        return switch (parameter.getType()) {
            case "string" -> new StringSchema();
            case "number" -> new NumberSchema();
            case "integer" -> new IntegerSchema();
            case "boolean" -> new BooleanSchema();
            case "object" -> new ObjectSchema();
            case "array" -> new ArraySchema().items(schemaFromParameter(toParameter(parameter.getItems())));
            default -> new Schema<>().$ref("#/components/schemas/" + parameter.getType());
        };
    }

    private static io.swagger.v3.oas.models.parameters.Parameter makeQueryParam(String name, String description, Schema<?> schema) {
        return new io.swagger.v3.oas.models.parameters.Parameter()
                .name(name)
                .in("query")
                .required(false)
                .description(description)
                .allowEmptyValue(true)
                .schema(schema);
    }

    private static Parameter toParameter(ArrayItems itemsParam) {
        return new Parameter(itemsParam.getType(), false, itemsParam.getItems());
    }

}