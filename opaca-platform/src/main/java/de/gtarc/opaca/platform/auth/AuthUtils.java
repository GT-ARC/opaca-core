package de.gtarc.opaca.platform.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gtarc.opaca.model.User;
import de.gtarc.opaca.platform.PlatformConfig;
import de.gtarc.opaca.util.KeycloakUtil;
import de.gtarc.opaca.util.RestHelper;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.authorization.ResourceServerRepresentation;
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
import java.util.UUID;
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
    public String generateTokenForUser(String username, String password) throws IOException {
        return KeycloakUtil.getTokenForUser(config.keycloakRealm, config.keycloakClientId, username, password);
    }

    /**
     * Create a temporary sub-user, derived from the user of the current request, to be used by
     * Containers started or other Runtime Platforms connected by that user.
     *
     * @param username the name of the new user to be created
     * @param owner the name of the user creating the new user (ignored if no auth)
     */
    public User createTempSubUser(String username, String owner) throws IOException {

        //var userToken = KeycloakUtil.getTokenForUser("http://localhost:8888/realms/opaca", "opaca-rp", "test", "test1234");
        //System.out.println(userToken);

        //var adminToken = KeycloakUtil.getTokenForUser("http://localhost:8888/realms/master", "admin-cli", "admin", "admin");
        //System.out.println(adminToken);

        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl("http://localhost:8888/")
                .realm("master")
                .clientId("admin-cli")
                .grantType("password")
                .username("admin")
                .password("admin")
                .build();

        var clientId = "1234567890";
        var secret = "OC5lcOXvNpeVQ3oCZcEF6qZjvEfhrztE";

        ClientRepresentation clientRep = new ClientRepresentation();
        clientRep.setClientId(clientId);
        clientRep.setSecret(secret);
        clientRep.setProtocol("openid-connect");
        clientRep.setEnabled(true);
        clientRep.setPublicClient(false);
        clientRep.setStandardFlowEnabled(false);
        clientRep.setClientAuthenticatorType("client-secret");
        clientRep.setDirectAccessGrantsEnabled(false);
        clientRep.setServiceAccountsEnabled(true);
        //clientRep.setAuthorizationSettings();
        /*
        "clientId": "my-client",
        "enabled": true,
        "protocol": "openid-connect",
        "publicClient": false,
        "serviceAccountsEnabled": true,
        "clientAuthenticatorType": "client-secret",
        "redirectUris": ["https://app.example/*"],
        "directAccessGrantsEnabled": true
         */

        var res2 = keycloak.realm("opaca").clients().create(clientRep);
        System.out.println("RESPONSE " + res2.getStatus());

        var clientToken = KeycloakUtil.getTokenForClient("http://localhost:8888/realms/opaca", clientId, secret);
        System.out.println("TOKEN " + clientToken);

        var id = keycloak.realm("opaca").clients().findByClientId(clientId).get(0).getId();
        System.out.println(id);
        var res3 = keycloak.realm("opaca").clients().delete(id);
        System.out.println("RESPONSE " + res3.getStatus());


        // TODO https://stackoverflow.com/questions/52230634/issuing-api-keys-using-keycloak
        //  Here:
        //  - create temp client with client-id = container-id
        //  - get and return the client secret
        //  - add client secret and KC URL to the container's env
        //  Inside the container:
        //  - get access token as above, with client-id as username and secret as password
        return null; // TODO
    }

    public static void main(String[] args) throws Exception {
        new AuthUtils().createTempSubUser(null, null);
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
