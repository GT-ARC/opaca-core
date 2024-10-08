package de.gtarc.opaca.platform;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.HashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import de.gtarc.opaca.model.AgentContainerImage;
import de.gtarc.opaca.model.Parameter;
import lombok.extern.java.Log;


/**
 * Used to validate actual action parameter values against required JSON Schema definition.
 */
@Log
public class ArgumentValidator {

    protected static final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    /** model definitions */
    private final Map<String, JsonSchema> definitions;

    private final Map<String, String> definitionsByUrl;

    public ArgumentValidator(AgentContainerImage image) {
        this.definitions = makeSchemas(image.getDefinitions());
        this.definitionsByUrl = image.getDefinitionsByUrl();
    }

    public boolean isArgsValid(Map<String, Parameter> parameters, Map<String, JsonNode> arguments) {
        if (isAnyArgumentMissing(parameters, arguments)) return false;
        if (isAnyArgumentRedundant(parameters, arguments)) return false;

        for (String name : arguments.keySet()) {
            var argument = arguments.get(name);
            var type = parameters.get(name).getType();
            var items = parameters.get(name).getItems();
            var optional = ! parameters.get(name).getRequired();
            if (! isArgumentValid(argument, type, optional, items)) return false;
        }

        return true;
    }

    private boolean isAnyArgumentMissing(Map<String, Parameter> parameters, Map<String, JsonNode> arguments) {
        return parameters.entrySet().stream()
                .anyMatch(entry -> entry.getValue().getRequired() && ! arguments.containsKey(entry.getKey()));
    }

    private boolean isAnyArgumentRedundant(Map<String, Parameter> parameters, Map<String, JsonNode> arguments) {
        return arguments.keySet().stream().anyMatch(name -> ! parameters.containsKey(name));
    }

    private boolean isArgumentValid(JsonNode node, String type, boolean optional, Parameter.ArrayItems items) {
        if (optional && node.isNull()) return true;
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
                if (! isArgumentValid(child, items.getType(), false, items.getItems())) return false;
            }
            return true;
        }
        return false;
    }

    private boolean isValidObject(JsonNode node, String type) {
        var definition = getSchema(type);
        if (definition == null) {
            log.warning("No definition found for type " + type + ", skipping type-checking.");
            return true;
        }
        var errors = definition.validate(node);
        return errors.isEmpty();
    }

    /**
     * Get Schema corresponding to type. This will lazily fetch and parse definitions-by-URL
     * and add them to the definitions map.
     */
    private JsonSchema getSchema(String type) {
        if (definitions.containsKey(type)) return definitions.get(type);
        if (!definitionsByUrl.containsKey(type)) return null;
        var url = definitionsByUrl.get(type);
        try {
            var schema = factory.getSchema(new URI(url));
            log.info("Created schema for " + type + " from " + url);
            definitions.put(type, schema);
            return schema;
        } catch (URISyntaxException e) {
            log.severe("Could not load schema for " + type + " from " + url + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Convert JSON Schema in JSON format to actual JSON Schema instances.
     */
    private Map<String, JsonSchema> makeSchemas(Map<String, JsonNode> originalDefinitions) {
        Map<String, JsonSchema> definitions = new HashMap<>();
        for (var type : originalDefinitions.keySet()) {
            var definition = factory.getSchema(originalDefinitions.get(type));
            definitions.put(type, definition);
        }
        return definitions;
    }

}
