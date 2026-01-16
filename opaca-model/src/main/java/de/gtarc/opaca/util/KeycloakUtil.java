package de.gtarc.opaca.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.stream.Collectors;

public class KeycloakUtil {

    public static String getTokenForUser(String keycloakUrl, String realm, String clientId, String username, String password) throws IOException {
        var data = Map.of(
                "grant_type", "password",
                "client_id", clientId,
                "username", username,
                "password", password
        );
        return getToken(keycloakUrl, realm, data);
    }

    public static String getTokenForClient(String keycloakUrl, String realm, String clientId, String clientSecret) throws IOException {
        var data = Map.of(
                "grant_type", "client_credentials",
                "client_id", clientId,
                "client_secret", clientSecret
        );
        return getToken(keycloakUrl, realm, data);
    }

    private static String getToken(String keycloakUrl, String realm, Map<String, String> data) throws IOException {
        String form = data.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        try {
            var response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token"))
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

    public static Map<String, Object> validateToken(String keycloak, String token) throws IOException {
        return null; // TODO
    }

}
