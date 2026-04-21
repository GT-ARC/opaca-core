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

    /** default value for the parameter; ignored if required, otherwise optional, should be of given type */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Object defaultValue = null;

    /** if type is 'array', this is the type of the array's items */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    ArrayItems items = null;

    /** short-hand for a required parameter */
    public Parameter(String type) {
        this.type = type;
    }

    /** short-hand for an optional parameter, with or without default */
    public Parameter(String type, Object defaultValue) {
        this.type = type;
        this.required = false;
        this.defaultValue = defaultValue;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class ArrayItems {
        @NonNull
        String type;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        ArrayItems items = null;
    }

}


