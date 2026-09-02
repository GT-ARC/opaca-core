package de.gtarc.opaca.platform.util;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.gtarc.opaca.model.*;
import de.gtarc.opaca.model.Parameter.ArrayItems;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.core.util.Yaml31;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.extern.log4j.Log4j2;

/**
 * Creates an Open-API compliant specification of all the Actions provided by the Agents on this platform,
 * that can be called using the /invoke route. This is complementary to the /api-docs route provided by
 * Swagger itself, which can only provide Open-API specifications for all the "static" services that are
 * part of the OPACA API, but not for the "dynamic" actions that may come and go at runtime.
 */
@Log4j2
public class ActionToOpenApi {

    public enum ActionFormat {
        JSON,
        YAML
    }

    private final static ObjectMapper mapper = Json31.mapper();

    /**
     * Create Open-API spec in JSON or YAML format for the actions in the given Agent Containers. This method
     * can provide an Open-API spec for all containers on the platform or for containers for this and connected
     * platforms, but to stay consistent with other similar OPACA routes like /agents or /info, only the containers
     * running on the platform itself should be passed.
     * 
     * @param agentsContainers List of agent containers currently running on this platform
     * @param format Whether to return the spec in JSON or YAML format
     * @param enableAuth Indicates if platform has authentication enabled
     * @return the OpenAPI schema as either JSON or YAML
     */
    public static String createOpenApiSchema(
            Collection<AgentContainer> agentsContainers,
            ActionFormat format,
            boolean enableAuth
    ) {
        // Check for custom definitions in agent container images and add to openapi components
        // Also check for external definitions by url
        Components components = new Components();
        for (AgentContainerImage image : agentsContainers.stream().map(AgentContainer::getImage).toList()) {
            for (var definition : image.getDefinitions().entrySet()) {
                try {
                    var node = mapper.valueToTree(definition.getValue());
                    addSchema(components, definition.getKey(), node);
                } catch (Exception e) {
                    log.warn("Failed to add definition {} from image {}: {}",
                            definition.getKey(), image.getImageName(), e.getMessage());
                }
            }
            for (var definition : image.getDefinitionsByUrl().entrySet()) {
                Schema<?> schema = new Schema<>().$ref(definition.getValue());
                components.addSchemas(definition.getKey(), schema);
            }
        }
        // Only create security component if auth is enabled
        if (enableAuth) {
            components.addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT"));
        }

        // Loop through each container and agent to add Paths for each action to the openapi spec
        Paths paths = new Paths();
        for (var container : agentsContainers) {
            for (var agent : container.getAgents()) {
                for (Action action : agent.getActions()) {

                    // Request Body
                    Schema<?> requestBodySchema = new ObjectSchema();
                    List<String> requiredList = new ArrayList<>();
                    for (var parameter : action.getParameters().entrySet()) {
                        requestBodySchema.addProperty(
                                parameter.getKey(),
                                schemaFromParameter(parameter.getValue(), components)
                        );
                        if (parameter.getValue().isRequired()) {
                            requiredList.add(parameter.getKey());
                        }
                    }
                    requestBodySchema.setRequired(requiredList);
                    RequestBody requestBody = new RequestBody()
                            .content(new Content().addMediaType("application/json", new MediaType().schema(requestBodySchema)))
                            .required(true);

                    // Responses
                    var responseMediaType = new MediaType().schema(schemaFromParameter(action.getResult(), components));
                    ApiResponse response200 = new ApiResponse()
                            .description("OK")
                            .content(new Content().addMediaType("*/*", responseMediaType));
                    ApiResponse responseDefault = new ApiResponse()
                            .description("Unexpected error")
                            .content(new Content().addMediaType("application/json", new MediaType().schema(new Schema<>().$ref("#/components/schemas/Error"))));

                    // Path Item
                    PathItem pathItem = new PathItem().post(new Operation()
                            .requestBody(requestBody)
                            .responses(new ApiResponses().addApiResponse("200", response200).addApiResponse("default", responseDefault))
                            .description(action.getDescription())
                            .tags(List.of(agent.getAgentId()))
                            .operationId(container.getContainerId() + ";" + agent.getAgentId() + ";" + action.getName())
                            .addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter().$ref("#/components/parameters/timeoutParam"))
                            .addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter().$ref("#/components/parameters/containerIdParam"))
                            .addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter().$ref("#/components/parameters/forwardParam")));
                    paths.addPathItem(String.format("/invoke/%s/%s", action.getName(), agent.getAgentId()), pathItem);
                }
            }
        }

        // Only add query parameters and errors if any action was added to the path
        if (!paths.isEmpty()) {
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

        // Merge everything together
        OpenAPI openAPI = new OpenAPI(SpecVersion.V31)
                .info(new Info()
                        .title("Collection of actions provided by the agents running on the OPACA platform")
                        .version("0.2"))
                .paths(paths)
                .security(enableAuth ? List.of(new SecurityRequirement().addList("bearerAuth")) : null)
                .components(components);

        return switch (format) {
            case JSON -> Json31.pretty(openAPI);
            case YAML -> Yaml31.pretty(openAPI);
        };
    }

    /**
     * Move sub-schema defs and rewrite refs, then add the schema
     * to the OAS components object.
     */
    private static void addSchema(Components components, String name, JsonNode node) {
        if (node.isObject()) {
            ObjectNode schemaNode = (ObjectNode) node;
            var inlineDefs = schemaNode.remove("$defs");
            var nestedComponents = schemaNode.remove("components");
            var schemas = nestedComponents == null ? null : nestedComponents.get("schemas");
            moveDefs(components, inlineDefs);
            moveDefs(components, schemas);
            rewriteRefs(schemaNode);
            var reference = schemaNode.get("$ref");
            // make sure a schema's definition isnt replaced by a ref to itself
            if (schemaNode.size() == 1 && reference != null && reference.isTextual()
                    && reference.textValue().equals("#/components/schemas/" + name)) {
                return;
            }
        }
        components.addSchemas(name, mapper.convertValue(node, Schema.class));
    }

    /**
     * Recursively move definitions out of $defs and into components/schemas.
     */
    private static void moveDefs(Components components, JsonNode definitions) {
        if (definitions != null && definitions.isObject()) {
            definitions.properties().forEach(entry ->
                    addSchema(components, entry.getKey(), entry.getValue())
            );
        }
    }

    /**
     * Recursively rewrite all ref-paths containing $defs to components/schemas instead.
     */
    private static void rewriteRefs(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            var ref = objectNode.get("$ref");
            if (ref != null && ref.isTextual() && ref.textValue().startsWith("#/$defs/")) {
                var refPath = ref.textValue().substring("#/$defs/".length());
                objectNode.put("$ref", "#/components/schemas/" + refPath);
            }
            objectNode.properties().forEach(field ->
                    rewriteRefs(field.getValue())
            );
        } else if (node.isArray()) {
            node.forEach(ActionToOpenApi::rewriteRefs);
        }
    }

    public static Schema<?> schemaFromParameter(Parameter parameter, Components components) {
        return schemaFromParameter(parameter, "#/components/schemas/", components);
    }

    public static Schema<?> schemaFromParameter(Parameter parameter, String refPrefix, Components components) {
        if (parameter == null || parameter.getType().equals("null")) {
            var schema = new Schema<>();
            schema.addType("null");
            return schema;
        }

        var result = switch (parameter.getType()) {
            case "string" -> new StringSchema();
            case "number" -> new NumberSchema();
            case "integer" -> new IntegerSchema();
            case "boolean" -> new BooleanSchema();
            case "object" -> new ObjectSchema();
            case "array" -> new ArraySchema().items(schemaFromParameter(toParameter(parameter.getItems()), refPrefix, components));
            default -> isKnownSchema(components, parameter.getType())
                    ? new Schema<>().$ref(refPrefix + parameter.getType())
                    : new Schema<>();
        };
        if (parameter.getDefaultValue() != null) {
            result.setDefault(parameter.getDefaultValue());
        }
        return result;
    }

    private static boolean isKnownSchema(Components components, String name) {
        if (components == null) return false;
        var schemas = components.getSchemas();
        if (schemas == null) return false;
        if (!schemas.containsKey(name)) {
            log.warn("Unknown schema: {} (known schemas: {})\n", name, schemas.keySet());
            return false;
        }
        return true;
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

    public static Parameter toParameter(ArrayItems itemsParam) {
        if (itemsParam == null) return null;
        return new Parameter(itemsParam.getType(), false, null, itemsParam.getItems());
    }

}