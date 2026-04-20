package de.gtarc.opaca.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes parameters of an Action
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class Parameter {

    /** the type name; either a primitive, or 'array', or defined in definitions or definitionsByUrl of image */
    @NotNull
    String type;

    /** whether the parameter is required; if it's not, the default may be determined by the action itself */
    boolean required = true;

    /** if type is 'array', this is the type of the array's items */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Valid
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
        @NotNull
        String type;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Valid
        ArrayItems items = null;
    }

}
