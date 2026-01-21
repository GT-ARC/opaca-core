package de.gtarc.opaca.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helper class for interaction with Keycloak, especially for acquiring Access Tokens for Users or Clients, and for
 * validating tokens.
 */
public class KeycloakUtil {

    /**
     * Call getToken with payload for User login
     */
    public static String getTokenForUser(String keycloakUrlAndRealm, String clientId, String username, String password) throws IOException {
        var data = Map.of(
                "grant_type", "password",
                "client_id", clientId,
                "username", username,
                "password", password
        );
        return getToken(keycloakUrlAndRealm, data);
    }

    /**
     * Call getToken with payload for Client login.
     */
    public static String getTokenForClient(String keycloakUrlAndRealm, String clientId, String clientSecret) throws IOException {
        var data = Map.of(
                "grant_type", "client_credentials",
                "client_id", clientId,
                "client_secret", clientSecret
        );
        return getToken(keycloakUrlAndRealm, data);
    }

    private static String getToken(String keycloakUrlAndRealm, Map<String, String> data) throws IOException {
        String form = data.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        try {
            var response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(keycloakUrlAndRealm + "/protocol/openid-connect/token"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(form))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            JsonNode body = new ObjectMapper().readTree(response.body());
            if (response.statusCode() == 200) {
                return body.get("access_token").asText();
            } else {
                throw new IOException(String.format("Login failed (%s): %s", response.statusCode(), body.get("error_description").asText()));
            }
        } catch (InterruptedException e) {
            throw new IOException("Login failed: Interrupted");
        }
    }

    /**
     * Validate the given token, by trying to process it using the given Keycloak URL, then
     * checking the expiration date. Returns Map of the token's claims.
     */
    public static Map<String, Object> validateToken(String keycloakUrlAndRealm, String token) throws IOException {
        String jwksUri = keycloakUrlAndRealm + "/protocol/openid-connect/certs";
        JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(new URL(jwksUri));

        ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource));
        try {
            JWTClaimsSet claims = jwtProcessor.process(token, null);
            if (claims.getExpirationTime().before(new Date())) {
                throw new IllegalArgumentException("Token expired");
            }
            return claims.getClaims();
        } catch (Exception e) {
            throw new IOException("Verifying JWT failed", e);
        }
    }

}
