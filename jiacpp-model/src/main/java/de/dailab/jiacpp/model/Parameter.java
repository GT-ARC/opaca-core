package de.dailab.jiacpp.model;

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;



@Data @AllArgsConstructor @NoArgsConstructor
public class Parameter {
    
    String name;
    
    String type;

    Boolean optional;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    Arrayitems items;

    public Parameter(String name, String type, Boolean optional)
    {
        this.name = name;
        this.type = type;
        this.optional = optional;
        
    }


}


