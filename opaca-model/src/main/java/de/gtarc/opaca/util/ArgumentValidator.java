package de.gtarc.opaca.util;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import de.gtarc.opaca.model.Parameter;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.core.type.TypeReference;

@AllArgsConstructor
public class ArgumentValidator {

    /** model definitions */
    Map<String, JsonSchema> definitions;

    public boolean isArgsValid(Map<String, Parameter> parameters, Map<String, JsonNode> arguments) {
        var anyArgMissing = isAnyArgumentMissing(parameters, arguments);
        var anyArgRedundant = isAnyArgumentRedundant(parameters, arguments);

        var argsValid = true;
        for (String name : arguments.keySet()) {
            var argument = arguments.get(name);
            var parameter = parameters.get(name);
            var type = parameter.getType();
            var items = parameter.getItems();
            var argValid = isValidArgument(type, items, argument);
            System.out.printf("validator - validating arg \"%s\": %s, %s -> %s%n", name, parameter, argument.toPrettyString(), argValid);
            if (!argValid) {
                argsValid = false;
                break;
            }
        }

        var result = !anyArgMissing && !anyArgRedundant && argsValid;
        System.out.printf("validator - ARGS VALID? %s (%s, %s, %s)%n", result, !anyArgMissing, !anyArgRedundant, argsValid);
        return result;
    }

    private boolean isAnyArgumentMissing(Map<String, Parameter> parameters, Map<String, JsonNode> arguments) {
        for (String name : parameters.keySet()) {
            var parameter = parameters.get(name);
            var argument = arguments.get(name);
            if (parameter.getRequired() && argument == null) {
                System.out.printf("validator - argument missing: %s, %s%n", name, parameter);
                return true;
            }
        }
        return false;
    }

    private boolean isAnyArgumentRedundant(Map<String, Parameter> parameters, Map<String, JsonNode> arguments) {
        for (String name : arguments.keySet()) {
            var parameter = parameters.get(name);
            var argument = arguments.get(name);
            if (parameter == null) {
                System.out.printf("validator - argument redundant: %s, %s%n", name, argument);
                return true;
            }
        }
        return false;
    }

    private boolean isValidArgument(String type, Parameter.ArrayItems items, JsonNode argument) {
        var prim = isValidPrimitive(type, argument);
        if (!prim) System.out.printf("validator - invalid primitive: %s, %s%n", type, argument);
        return prim || isValidList(type, items, argument)
                || isValidObject(type, argument); // <- for prints
    }

    private boolean isValidPrimitive(String type, JsonNode node) {
        switch (type) {
            case "Integer": case "Int":
                System.out.println("validator - primitive type Integer");
                return node.asInt() == Integer.parseInt(node.asText());
            case "Double": case "Float": case "Decimal":
                System.out.println("validator - primitive type Double");
                return node.asDouble() == Double.parseDouble(node.asText());
            case "Boolean": case "Bool":
                System.out.println("validator - primitive type Boolean");
                return node.asBoolean() == Boolean.parseBoolean(node.asText());
            case "String": case "Str": case "Text":
                System.out.println("validator - primitive type String");
                System.out.printf("validator - testing primitive type string: %s, %s%n", node.isTextual(), node.asText());
                return node.isTextual() || !node.asText().isEmpty();
            default:
                System.out.printf("validator - %s is not primitive%n", type);
                return false;
        }
    }

    private boolean isValidList(String type, Parameter.ArrayItems items, JsonNode node) {
        if ((!type.equals("List") && !type.equals("Array")) || items == null) return false;
        try {
            var typeRef = new TypeReference<List<JsonNode>>(){};
            ObjectMapper mapper = RestHelper.mapper;
            var list = mapper.readValue(node.traverse(), typeRef);
            for (JsonNode itemNode : list) {
                if (!isValidArgument(items.getType(), items.getItems(), itemNode)) {
                    System.out.printf("validator - invalid list: %s, %s, %s%n", type, items, itemNode);
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }

    }

    private boolean isValidObject(String type, JsonNode node) {
        var definition = definitions.get(type);
        System.out.printf("validator - trying to validate if object is type %s: %s, %s%n", type, node.toPrettyString(), definition);
        if (definition == null) return false;
        var errors = definition.validate(node);
        if (!errors.isEmpty()) {
            System.out.printf("validator - invalid objects: %s, %s%n", type, node);
            return false;
        }
        return true;
    }

}
