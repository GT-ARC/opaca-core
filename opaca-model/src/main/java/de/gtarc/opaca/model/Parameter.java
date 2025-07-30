package de.gtarc.opaca.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Describes parameters of an Action
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class Parameter {

    /** the type name; either a primitive, or 'array', or defined in definitions or definitionsByUrl of image */
    @NonNull
    String type;

    /** whether the parameter is required; if it's not, the default may be determined by the action itself */
    Boolean required = true;

    /** if type is 'array', this is the type of the array's items */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    ArrayItems items = null;

    public Parameter(String type) {
        this.type = type;
    }

    public Parameter(String type, Boolean required) {
        this.type = type;
        this.required = required;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class ArrayItems {
        @NonNull
        String type;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        ArrayItems items = null;
    }

}


