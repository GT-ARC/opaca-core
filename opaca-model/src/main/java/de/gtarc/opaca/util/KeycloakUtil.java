package de.gtarc.opaca.util;

import tools.jackson.databind.JsonNode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import de.gtarc.opaca.model.TokenPair;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Helper class for interaction with Keycloak, especially for acquiring Access Tokens for Users or Clients, and for
 * validating tokens.
 */
public class KeycloakUtil {

    /**
     * Call getToken with payload for User login
     */
    public static TokenPair getTokenForUser(String keycloakUrlAndRealm, String clientId, String username, String password) throws IOException {
        var data = Map.of(
                "grant_type", "password",
                "client_id", clientId,
                "username", username,
                "password", password
        );
        return getTokens(keycloakUrlAndRealm, data);
    }

    public static TokenPair refreshToken(String keycloakUrlAndRealm, String clientId, String refreshToken) throws IOException {
        var data = Map.of(
                "grant_type", "refresh_token",
                "client_id", clientId,
                "refresh_token", refreshToken
        );
        return getTokens(keycloakUrlAndRealm, data);
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
        return getTokens(keycloakUrlAndRealm, data).getAccessToken();
    }

    /**
     * Get token for the Keycloak admin user (in the 'master' realm), needed for managing clients via the admin REST API.
     * The keycloakUrl is the plain server URL, i.e. without the '/realms/...' part.
     */
    public static String getAdminToken(String keycloakUrl, String adminUser, String adminPassword) throws IOException {
        return getTokenForUser(keycloakUrl + "/realms/master", "admin-cli", adminUser, adminPassword).getAccessToken();
    }

    /**
     * Create a private client (service account only, no interactive login) with the given secret in the given realm,
     * using the Keycloak admin REST API. Throws IOException (RestHelper.RequestException) if this fails.
     */
    public static void createClient(String keycloakUrl, String realm, String adminToken,
                                    String clientId, String description, String secret) throws IOException {
        var clientRep = new LinkedHashMap<String, Object>();
        clientRep.put("clientId", clientId);
        clientRep.put("description", description);
        clientRep.put("secret", secret);
        clientRep.put("protocol", "openid-connect");
        clientRep.put("clientAuthenticatorType", "client-secret");
        clientRep.put("publicClient", false);
        clientRep.put("standardFlowEnabled", false);
        clientRep.put("directAccessGrantsEnabled", false);
        clientRep.put("serviceAccountsEnabled", true);
        adminClient(keycloakUrl, adminToken).post("/admin/realms/" + realm + "/clients", clientRep, null);
    }

    /**
     * Delete the client with the given client-ID (not the internal UUID) from the given realm.
     * Returns false if no such client exists.
     */
    public static boolean deleteClient(String keycloakUrl, String realm, String adminToken, String clientId) throws IOException {
        var rest = adminClient(keycloakUrl, adminToken);
        var clients = rest.get("/admin/realms/" + realm + "/clients?clientId="
                + URLEncoder.encode(clientId, StandardCharsets.UTF_8), JsonNode.class);
        if (clients == null || clients.isEmpty()) {
            return false;
        }
        rest.delete("/admin/realms/" + realm + "/clients/" + clients.get(0).get("id").asString(), null, null);
        return true;
    }

    private static RestHelper adminClient(String keycloakUrl, String adminToken) {
        var rest = new RestHelper(keycloakUrl);
        rest.token = adminToken;
        return rest;
    }

    private static TokenPair getTokens(String keycloakUrlAndRealm, Map<String, String> data) throws IOException {
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
            JsonNode body = RestHelper.readJson(response.body());
            if (response.statusCode() == 200) {
                return new TokenPair(
                        body.get("access_token").asString(),
                        body.optional("refresh_token").map(JsonNode::asString).orElse(null)
                );
            } else {
                throw new IOException(String.format("Login failed (%s): %s", response.statusCode(), body.get("error_description").asString()));
            }
        } catch (InterruptedException e) {
            throw new IOException("Login failed: Interrupted");
        }
    }


    private static final Map<String, JWKSource<SecurityContext>> cachedSources = new ConcurrentHashMap<>();

    /**
     * Validate the given token, by trying to process it using the given Keycloak URL, then
     * checking the expiration date. Returns Map of the token's claims.
     */
    public static Map<String, Object> validateToken(String keycloakUrlAndRealm, String token) throws IOException {
        String jwksUri = keycloakUrlAndRealm + "/protocol/openid-connect/certs";
        if (! cachedSources.containsKey(jwksUri)) {
            cachedSources.put(jwksUri, new RemoteJWKSet(URI.create(jwksUri).toURL()));
        }
        JWKSource<SecurityContext> keySource = cachedSources.get(jwksUri);

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
