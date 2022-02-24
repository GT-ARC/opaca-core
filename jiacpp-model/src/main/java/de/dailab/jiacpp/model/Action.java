package de.dailab.jiacpp.model;

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
    String name;

    /** parameter names and types */
    Map<String, String> parameters;

    /** type of result */
    String result;

}
