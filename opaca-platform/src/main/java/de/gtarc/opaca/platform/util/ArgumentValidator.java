package de.gtarc.opaca.platform.util;

import java.util.Map;
import java.util.HashMap;

import com.fasterxml.jackson.databind.JsonNode;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.InputFormat; // Required to validate String configurations safely
import de.gtarc.opaca.model.AgentContainerImage;
import de.gtarc.opaca.model.Parameter;
import lombok.extern.log4j.Log4j2;

/**
 * Used to validate actual action parameter values against required JSON Schema
 * definition.
 */
@Log4j2
public class ArgumentValidator {

    protected static final SchemaRegistry registry = SchemaRegistry
            .withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    /** model definitions */
    private final Map<String, Schema> definitions;

    private final Map<String, String> definitionsByUrl;

    public ArgumentValidator(AgentContainerImage image) {
        this.definitions = makeSchemas(image.getDefinitions());
        this.definitionsByUrl = image.getDefinitionsByUrl();
    }

    public boolean isArgsValid(Map<String, Parameter> parameters, Map<String, JsonNode> arguments) {
        if (isAnyArgumentMissing(parameters, arguments))
            return false;
        if (isAnyArgumentRedundant(parameters, arguments))
            return false;

        for (String name : arguments.keySet()) {
            var argument = arguments.get(name);
            var type = parameters.get(name).getType();
            var items = parameters.get(name).getItems();
            var optional = !parameters.get(name).isRequired();
            if (!isArgumentValid(argument, type, optional, items))
                return false;
        }

        return true;
    }

    private boolean isAnyArgumentMissing(Map<String, Parameter> parameters, Map<String, JsonNode> arguments) {
        return parameters.entrySet().stream()
                .anyMatch(entry -> entry.getValue().isRequired() && !arguments.containsKey(entry.getKey()));
    }

    private boolean isAnyArgumentRedundant(Map<String, Parameter> parameters, Map<String, JsonNode> arguments) {
        return arguments.keySet().stream().anyMatch(name -> !parameters.containsKey(name));
    }

    private boolean isArgumentValid(JsonNode node, String type, boolean optional, Parameter.ArrayItems items) {
        if (optional && node.isNull())
            return true;
        return switch (type) {
            case "integer" -> node.isInt();
            case "number" -> node.isNumber();
            case "boolean" -> node.isBoolean();
            case "string" -> node.isTextual();
            case "null" -> node.isNull();
            case "array" -> isValidList(node, items);
            default -> isValidObject(node, type);
        };
    }

    private boolean isValidList(JsonNode node, Parameter.ArrayItems items) {
        if (node.isArray() && items != null) {
            for (JsonNode child : node) {
                if (!isArgumentValid(child, items.getType(), false, items.getItems()))
                    return false;
            }
            return true;
        }
        return false;
    }

    private boolean isValidObject(JsonNode node, String type) {
        var definition = getSchema(type);
        if (definition == null) {
            log.warn("No definition found for type {}, skipping type-checking.", type);
            return true;
        }
        var errors = definition.validate(node.toString(), InputFormat.JSON);
        return errors.isEmpty();
    }

    /**
     * Get Schema corresponding to type. This will lazily fetch and parse
     * definitions-by-URL and add them to the definitions map.
     */
    private Schema getSchema(String type) {
        if (definitions.containsKey(type))
            return definitions.get(type);
        if (!definitionsByUrl.containsKey(type))
            return null;
        var url = definitionsByUrl.get(type);
        try {
            var schema = registry.getSchema(SchemaLocation.of(url));
            log.info("Created schema for {} from {}", type, url);
            definitions.put(type, schema);
            return schema;
        } catch (Exception e) {
            log.error("Could not load schema for {} from {}: {}", type, url, e.getMessage());
            return null;
        }
    }

    /**
     * Convert JSON Schema in JSON format to actual JSON Schema instances.
     */
    private Map<String, Schema> makeSchemas(Map<String, JsonNode> originalDefinitions) {
        Map<String, Schema> definitions = new HashMap<>();
        for (var type : originalDefinitions.keySet()) {
            var schemaJsonString = originalDefinitions.get(type).toString();
            var definition = registry.getSchema(schemaJsonString);
            definitions.put(type, definition);
        }
        return definitions;
    }
}
