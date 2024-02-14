package de.gtarc.opaca.platform;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import de.gtarc.opaca.model.AgentContainerImage;
import de.gtarc.opaca.model.Parameter;
import de.gtarc.opaca.util.RestHelper;
import com.fasterxml.jackson.core.type.TypeReference;


public class ArgumentValidator {

    protected static final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    /** model definitions */
    Map<String, JsonSchema> definitions;

    Map<String, String> definitionsByUrl;

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
            if (isArgumentInvalid(argument, type, items)) return false;
        }

        return true;
    }

    private boolean isAnyArgumentMissing(Map<String, Parameter> parameters, Map<String, JsonNode> arguments) {
        for (String name : parameters.keySet()) {
            var parameter = parameters.get(name);
            var argument = arguments.get(name);
            if (parameter.getRequired() && argument == null) return true;
        }
        return false;
    }

    private boolean isAnyArgumentRedundant(Map<String, Parameter> parameters, Map<String, JsonNode> arguments) {
        for (String name : arguments.keySet()) {
            var parameter = parameters.get(name);
            if (parameter == null) return true;
        }
        return false;
    }

    private boolean isArgumentInvalid(JsonNode argument, String type, Parameter.ArrayItems items) {
        return !isValidPrimitive(argument, type) && !isValidList(argument, type, items)
                && !isValidObject(argument, type);
    }

    private boolean isValidPrimitive(JsonNode node, String type) {
        switch (type) {
            case "integer":
                return node.isInt();
            case "number":
                return node.isNumber();
            case "boolean":
                return node.isBoolean();
            case "string":
                return node.isTextual();
            case "null":
                return node.isNull();
            default:
                return false;
        }
    }

    private boolean isValidList(JsonNode node, String type, Parameter.ArrayItems items) {
        if (!type.equals("array") || items == null) return false;
        try {
            var typeRef = new TypeReference<List<JsonNode>>(){};
            var mapper = RestHelper.mapper;
            var list = mapper.readValue(node.traverse(), typeRef);
            for (JsonNode itemNode : list) {
                if (isArgumentInvalid(itemNode, items.getType(), items.getItems())) return false;
            }
            return true;
        } catch (IOException e) {
            return false;
        }

    }

    private boolean isValidObject(JsonNode node, String type) {
        var definition = getSchema(type);
        if (definition == null) return false;
        var errors = definition.validate(node);
        return errors.isEmpty();
    }

    private JsonSchema getSchema(String type) {
        if (definitions.containsKey(type)) return definitions.get(type);
        if (!definitionsByUrl.containsKey(type)) return null;
        var url = definitionsByUrl.get(type);
        try {
            System.out.printf("Creating schema for type \"%s\" from URL \"%s\" ...%n", type, url);
            var schema = factory.getSchema(new URI(url));
            definitions.put(type, schema);
            return schema;
        } catch (URISyntaxException e) {
            System.err.println(e.getMessage());
            return null;
        }
    }

    private Map<String, JsonSchema> makeSchemas(Map<String, JsonNode> originalDefinitions) {
        Map<String, JsonSchema> definitions = new HashMap<>();
        for (var type : originalDefinitions.keySet()) {
            var definition = factory.getSchema(originalDefinitions.get(type));
            definitions.put(type, definition);
        }
        return definitions;
    }

}
