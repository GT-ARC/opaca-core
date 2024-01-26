package de.gtarc.opaca.util;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import de.gtarc.opaca.model.Parameter;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ArgumentValidator {

    /** model definitions */
    Map<String, JsonSchema> definitions;

    /** action parameter definitions */
    Map<String, Parameter> parameters;

    public boolean isArgsValid(Map<String, JsonNode> arguments) {
        if (this.isAnyArgumentMissing(arguments)) return false;
        if (this.isAnyArgumentRedundant(arguments)) return false;

        for (String name : arguments.keySet()) {
            var argument = arguments.get(name);
            System.out.printf("validating arg\"%s\": %s%n", name, argument.toPrettyString());
            return isValidPrimitive(name, argument)
                    || isValidObject(name, argument);
        }
        return true;
    }

    private boolean isAnyArgumentMissing(Map<String, JsonNode> arguments) {
        for (String name : parameters.keySet()) {
            var parameter = parameters.get(name);
            if (parameter.getRequired() && arguments.get(name) == null) return false;
        }
        return true;
    }

    private boolean isAnyArgumentRedundant(Map<String, JsonNode> arguments) {
        for (String name : arguments.keySet()) {
            if (parameters.get(name) == null) return true;
        }
        return false;
    }

    private boolean isValidPrimitive(String name, JsonNode argument) {
        var parameter = parameters.get(name);
        switch (parameter.getType()) {
            case "Integer": case "Int":
                return argument.isInt();
            case "Double": case "Float": case "Decimal":
                return argument.isFloat() || argument.isDouble();
            case "Boolean": case "Bool":
                return argument.isBoolean();
            case "String": case "Str":
                return argument.isTextual();
            default: return false;
        }
    }

    private boolean isValidObject(String name, JsonNode argument) {
        var parameter = parameters.get(name);
        var definition = definitions.get(parameter.getType());
        var errors = definition.validate(argument);
        return errors.isEmpty();
    }

}
