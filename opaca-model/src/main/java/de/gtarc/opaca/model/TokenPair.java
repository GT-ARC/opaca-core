package de.gtarc.opaca.model;


import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Wrapper for Access-Token and Refresh-Token returned by login-route.
 */
@Data @AllArgsConstructor @NoArgsConstructor
@ToString(exclude = {"accessToken", "refreshToken"})
public class TokenPair {

    /** short-lived Keycloak access token to be used for regular requests to the OPACA API */
    @NotNull
    String accessToken;

    /** long-lived refresh token that can be used once to get a new access-token (only for username/password login) */
    String refreshToken;

}
