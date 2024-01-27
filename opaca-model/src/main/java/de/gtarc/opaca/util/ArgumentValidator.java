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

    public boolean isArgsValid(Map<String, Parameter> parameters, Map<String, JsonNode> arguments) {
        if (this.isAnyArgumentMissing(parameters, arguments)) return false;
        if (this.isAnyArgumentRedundant(parameters, arguments)) return false;

        for (String name : arguments.keySet()) {
            var argument = arguments.get(name);
            var parameter = parameters.get(name);
            System.out.printf("validating arg\"%s\": %s%n", name, argument.toPrettyString());
            return isValidPrimitive(parameter, argument)
                    || isValidObject(parameter, argument);
        }
        return true;
    }

    private boolean isAnyArgumentMissing(Map<String, Parameter> parameters, Map<String, JsonNode> arguments) {
        for (String name : parameters.keySet()) {
            var parameter = parameters.get(name);
            if (parameter.getRequired() && arguments.get(name) == null) return false;
        }
        return true;
    }

    private boolean isAnyArgumentRedundant(Map<String, Parameter> parameters, Map<String, JsonNode> arguments) {
        for (String name : arguments.keySet()) {
            if (parameters.get(name) == null) return true;
        }
        return false;
    }

    private boolean isValidPrimitive(Parameter parameter, JsonNode argument) {
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

    private boolean isValidObject(Parameter parameter, JsonNode argument) {
        var definition = definitions.get(parameter.getType());
        var errors = definition.validate(argument);
        return errors.isEmpty();
    }

}
