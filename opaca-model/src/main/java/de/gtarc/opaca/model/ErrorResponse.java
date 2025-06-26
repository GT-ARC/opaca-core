package de.gtarc.opaca.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    public Integer statusCode;

    public String message;

    public ErrorResponse cause = null;

    public static ErrorResponse from(Throwable e) {
        if (e == null) return null;
        return new ErrorResponse(null, e.getMessage(), ErrorResponse.from(e.getCause()));
    }

}
