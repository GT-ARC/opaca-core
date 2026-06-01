package de.gtarc.opaca.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Describes an action provided by one or more agents.
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class Action {

    /** name of the action */
    @NotNull
    String name;

    /** optional human-readable description of what this action does */
    String description;

    /** parameter names and types */
    @NotNull @Valid
    Map<String, Parameter> parameters = Map.of();

    /** type of result */
    @Valid
    Parameter result;

    public Action(String name, Map<String, Parameter> parameters, Parameter result) {
        this(name, null, parameters, result);
    }

}
