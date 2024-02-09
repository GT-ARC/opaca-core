package de.gtarc.opaca.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data @AllArgsConstructor @NoArgsConstructor
public class Parameter {
    
    String type;

    Boolean required = true;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    ArrayItems items = null;

    public Parameter(String type, Boolean required) {
        this.type = type;
        this.required = required;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class ArrayItems {
        String type;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        ArrayItems items;
    }

}


