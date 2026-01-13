package de.gtarc.opaca.platform.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gtarc.opaca.model.User;
import de.gtarc.opaca.platform.PlatformConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuthUtils {

    @Autowired
    private PlatformConfig config;

    // FORMER METHODS OF JWT UTIL

    // used for JWTs for containers and connected platform, also for token-renewal
    public String generateToken(String owner, Duration duration) {
        return null; // TODO
    }

    // FORMER METHODS OF TOKEN USER DETAILS SERVICE

    // used for platform login
    public String generateTokenForUser(String username, String password) throws Exception {
        var data = Map.of(
                "grant_type", "password",
                "client_id", config.keycloakClientId,
                "username", username,
                "password", password
        );
        String form = data.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create(config.keycloakRealm + "/protocol/openid-connect/token"))
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
    }

    /**
     * Create a temporary sub-user, derived from the user of the current request, to be used by
     * Containers started or other Runtime Platforms connected by that user.
     *
     * @param username the name of the new user to be created
     * @param owner the name of the user creating the new user (ignored if no auth)
     */
    public User createTempSubUser(String username, String owner) {
        return null; // TODO
    }

    /**
     * Removes a user from the UserRepository.
     * Return true if the user was deleted, false if not.
     * If the user does not exist, throw exception.
     */
    public Boolean removeUser(String username) {
        return null; // TODO
    }

    /**
     * Checks if the current request user is either an admin (has full control over user management)
     * or the request user is performing request on its own data
     * @param username: Name of user which will get affected by request (NOT THE CURRENT REQUEST USER)
     */
    // used in remove-container
    public boolean isAdminOrSelf(String username) {
        return false; // TODO
    }

    /*
     * CONTAINER TOKEN STUFF... store those in KeyCloak, or just in-memory?
     */

    private final Map<String, Map<String, String>> containerTokens = new HashMap<>();

    /**
     * Returns Access Token associated with container, or null
     */
    public String getContainerToken(String username, String containerId) {
        return containerTokens.getOrDefault(username, Map.of()).getOrDefault(containerId, null);
    }

    /**
     * Associate Access Token with container, overwrite existing if any
     */
    public void addContainerToken(String username, String containerId, String token) {
        containerTokens.computeIfAbsent(username, s -> new HashMap<>()).put(containerId, token);
    }

    /**
     * Delete Access Token associated with container, return old token if existed, otherwise null
     */
    public String removeContainerToken(String username, String containerId) {
        return containerTokens.getOrDefault(username, Map.of()).remove(containerId);
    }

}
