package de.dailab.jiacpp.model;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;



@Data @AllArgsConstructor @NoArgsConstructor
public class ObjectDefinition {
    
    String inherits;
    Map<String, Map<String, String>> parameters;


}
