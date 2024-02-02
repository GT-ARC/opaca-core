package de.gtarc.opaca.util.validation;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import de.gtarc.opaca.model.Parameter;
import de.gtarc.opaca.util.RestHelper;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.core.type.TypeReference;

@AllArgsConstructor
public class ArgumentValidator {

    /** model definitions */
    Map<String, JsonSchema> definitions;

    Map<String, String> definitionsByUrl;

    public boolean isArgsValid(Map<String, Parameter> parameters, Map<String, JsonNode> arguments) {
        System.out.printf("validator - validating args: %s, %s%n", parameters.toString(), arguments.toString());

        if (isAnyArgumentMissing(parameters, arguments)) {
            System.out.println("validator - arguments missing");
            return false;
        }
        if (isAnyArgumentRedundant(parameters, arguments)) {
            System.out.println("validator - arguments redundant");
            return false;
        }

        for (String name : arguments.keySet()) {
            var argument = arguments.get(name);
            var type = parameters.get(name).getType();
            var items = parameters.get(name).getItems();
            if (isArgumentInvalid(argument, type, items)) {
                System.out.printf("validator - argument invalid: %s, %s, %s%n", name, type, argument);
                return false;
            }
        }
        System.out.println("validator - arguments valid");
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
        if (!type.equals("array") || items == null)
            return false;
        try {
            var typeRef = new TypeReference<List<JsonNode>>(){};
            ObjectMapper mapper = RestHelper.mapper;
            var list = mapper.readValue(node.traverse(), typeRef);
            for (JsonNode itemNode : list) {
                if (isArgumentInvalid(itemNode, items.getType(), items.getItems()))
                    return false;
            }
            return true;
        } catch (IOException e) {
            return false;
        }

    }

    private boolean isValidObject(JsonNode node, String type) {
        var definition = definitions.get(type);
        if (definition == null) return false;
        var errors = definition.validate(node);
        return errors.isEmpty();
    }

}
