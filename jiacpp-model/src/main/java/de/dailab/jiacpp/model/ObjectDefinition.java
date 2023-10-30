package de.dailab.jiacpp.model;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;



@Data @AllArgsConstructor @NoArgsConstructor
public class ObjectDefinition {
    // String name
    String inherits;

    String type;

    Map<String, Parameter> parameters;


}
