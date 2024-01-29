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
        if (this.isAnyArgumentMissing(parameters, arguments)) return false;
        if (this.isAnyArgumentRedundant(parameters, arguments)) return false;

        for (String name : arguments.keySet()) {
            var argument = arguments.get(name);
            var parameter = parameters.get(name);
            var type = parameter.getType();
            var items = parameter.getItems();
            var result = this.isValidArgument(type, items, argument);
            System.out.printf("validator - validating arg \"%s\": %s, %s -> %s%n", name, parameter, argument.toPrettyString(), result);
            return result;
        }
        return true;
    }

    private boolean isAnyArgumentMissing(Map<String, Parameter> parameters, Map<String, JsonNode> arguments) {
        for (String name : parameters.keySet()) {
            var parameter = parameters.get(name);
            var argument = arguments.get(name);
            if (parameter.getRequired() && argument == null) {
                System.out.printf("validator - argument missing: %s, %s%n", name, parameter);
                return false;
            }
        }
        return true;
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
        if (!prim) System.out.printf("validator - invalid primitive: %s, %s", type, argument);
        return prim || isValidList(type, items, argument)
                || isValidObject(type, argument) || true; // <- for prints
    }

    private boolean isValidPrimitive(String type, JsonNode node) {
        switch (type) {
            case "Integer": case "Int":
                return node.isInt();
            case "Double": case "Float": case "Decimal":
                return node.isFloat() || node.isDouble();
            case "Boolean": case "Bool":
                return node.isBoolean();
            case "String": case "Str":
                return node.isTextual();
            default: return false;
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
                    System.out.printf("validator - invalid list: %s, %s, %s", type, items, itemNode);
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
        var errors = definition.validate(node);
        if (!errors.isEmpty()) {
            System.out.printf("validator - invalid objects: %s, %s", type, node);
            return false;
        }
        return true;
    }

}
