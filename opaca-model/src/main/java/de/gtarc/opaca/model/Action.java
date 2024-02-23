package de.gtarc.opaca.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Map;

/**
 * Describes an action provided by one or more agents.
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class Action {

    /** name of the action */
    @NonNull
    String name;

    /** parameter names and types */
    @NonNull
    Map<String, Parameter> parameters = Map.of();

    /** type of result */
    @NonNull
    String result;

}
