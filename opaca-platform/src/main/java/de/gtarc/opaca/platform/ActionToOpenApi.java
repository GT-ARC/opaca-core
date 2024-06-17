package de.gtarc.opaca.platform;

import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gtarc.opaca.model.*;

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
 * ActionToOpenApi
 */
public class ActionToOpenApi {

    public static String createOpenApiSchema(Collection<AgentContainer> agentsContainers) {
        // Check for custom definitions in agent container images and add to openapi components
        // Also check for external definitions by url
        Components components = new Components();
        ObjectMapper mapper = new ObjectMapper();
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

        // Add query parameters to components
        io.swagger.v3.oas.models.parameters.Parameter timeoutParameter = new io.swagger.v3.oas.models.parameters.Parameter()
                .name("timeout")
                .in("query")
                .required(false)
                .description("Timeout in seconds after which the action should abort")
                .allowEmptyValue(true)
                .schema(new IntegerSchema());
        io.swagger.v3.oas.models.parameters.Parameter containerIdParam = new io.swagger.v3.oas.models.parameters.Parameter()
                .name("containerId")
                .in("query")
                .required(false)
                .description("Id of the container on which the agent is running")
                .allowEmptyValue(true)
                .schema(new StringSchema());
        io.swagger.v3.oas.models.parameters.Parameter forwardParam = new io.swagger.v3.oas.models.parameters.Parameter()
                .name("forward")
                .in("query")
                .required(false)
                .description("Whether or not to include the connected runtime platforms")
                .allowEmptyValue(true)
                .schema(new BooleanSchema());
        components.addParameters("timeoutParam", timeoutParameter);
        components.addParameters("containerIdParam", containerIdParam);
        components.addParameters("forwardParam", forwardParam);

        // Add Error schema to components
        components.addSchemas("Error", new Schema<>().type("object")
                .addProperty("code", new Schema<>().type("integer"))
                .addProperty("message", new Schema<>().type("string")));

        // Add security scheme
        components.addSecuritySchemes("bearerAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT"));

        // Create Paths
        Paths paths = new Paths();

        // Loop through each container and agent to add each action to the openapi spec
        for (var container : agentsContainers) {
            for (var agent : container.getAgents()) {
                for (Action action : agent.getActions()) {

                    // Request Body
                    Schema<?> responseBodySchema = new Schema<>().type("object");
                    List<String> requiredList = new ArrayList<>();
                    for (var parameter : action.getParameters().entrySet()) {
                        responseBodySchema.addProperty(parameter.getKey(), schemaFromParameter(parameter.getValue()));
                        if (parameter.getValue().getRequired()) {
                            requiredList.add(parameter.getKey());
                        }
                    }
                    responseBodySchema.setRequired(requiredList);
                    RequestBody requestBody = new RequestBody()
                            .content(new Content().addMediaType("application/json", new MediaType().schema(responseBodySchema)))
                            .required(true);

                    // Responses
                    ApiResponse response200 = new ApiResponse()
                            .description("OK")
                            .content(new Content().addMediaType("*/*",
                                    new MediaType().schema(schemaFromParameter(action.getResult()))));
                    ApiResponse responseDefault = new ApiResponse()
                            .description("Unexpected error")
                            .content(new Content().addMediaType("application/json", new MediaType().schema(new Schema<>().$ref("#/components/schemas/Error"))));


                    // Path Item
                    PathItem pathItem = new PathItem().post(new Operation()
                            .requestBody(requestBody)
                            .responses(new ApiResponses().addApiResponse("200", response200).addApiResponse("default", responseDefault))
                            .description(action.getDescription() != null ? action.getDescription() : "")
                            .operationId(container.getContainerId() + "-" + agent.getAgentId() + "-" + action.getName())
                            .addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter().$ref("#/components/parameters/timeoutParam"))
                            .addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter().$ref("#/components/parameters/containerIdParam"))
                            .addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter().$ref("#/components/parameters/forwardParam")));
                    paths.addPathItem(String.format("/invoke/%s/%s", action.getName(), agent.getAgentId()), pathItem);
                }
            }
        }

        // Merge everything together
        OpenAPI openAPI = new OpenAPI();

        openAPI.setInfo(new Info()
                .title("Collection of actions provided by the agents running on the OPACA platform")
                .version("0.2"));
        openAPI.setPaths(paths);
        openAPI.setComponents(components);
        openAPI.setSecurity(List.of(new SecurityRequirement().addList("bearerAuth")));

        return Yaml.pretty(openAPI);
    }

    private static Schema<?> schemaFromParameter(Parameter parameter) {
        Schema<?> fieldSchema = new Schema<>();

        if (parameter == null) {
            fieldSchema.setType("object");
            fieldSchema.nullable(true);
            return fieldSchema;
        }

        switch (parameter.getType()) {
            case "string":
                fieldSchema.setType("string");
                break;
            case "number":
                fieldSchema.setType("number");
                break;
            case "integer":
                fieldSchema.setType("integer");
                break;
            case "boolean":
                fieldSchema.setType("boolean");
                break;
            case "object":
                fieldSchema.setType("object");
                break;
            case "array":
                fieldSchema.setType("array");
                fieldSchema.setItems(schemaFromParameter(new Parameter(parameter.getItems().getType(), false, parameter.getItems().getItems())));
                break;
            default:
                fieldSchema.$ref("#/components/schemas/" + parameter.getType());
                break;
        }
        return fieldSchema;
    }

}