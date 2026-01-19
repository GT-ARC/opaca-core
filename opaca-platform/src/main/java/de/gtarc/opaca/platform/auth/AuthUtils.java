package de.gtarc.opaca.platform.auth;

import de.gtarc.opaca.platform.PlatformConfig;
import de.gtarc.opaca.util.KeycloakUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.ClientRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

@Service
public class AuthUtils {

    @Autowired
    private PlatformConfig config;

    /*
     * KEYCLOAK AUTHENTICATION
     */

    private Keycloak keycloakClient() {
        return KeycloakBuilder.builder()
                .serverUrl(config.keycloakUrl)
                .realm("master")
                .clientId("admin-cli")
                .grantType("password")
                .username(config.keycloakAdmin)
                .password(config.keycloakAdminPw)
                .build();
    }

    // used for platform login
    public String getTokenForUser(String username, String password) throws IOException {
        return KeycloakUtil.getTokenForUser(config.keycloakUrl, config.keycloakRealm, config.keycloakClientId, username, password);
    }

    // TODO https://stackoverflow.com/questions/52230634/issuing-api-keys-using-keycloak
    //  Here:
    //  - create temp client with client-id = container-id
    //  - get and return the client secret
    //  - add client secret and KC URL to the container's env
    //  Inside the container:
    //  - get access token as above, with client-id as username and secret as password

    public String createClientAndGetSecret(String clientId) throws  IOException {
        var secret = UUID.randomUUID().toString();

        ClientRepresentation clientRep = new ClientRepresentation();
        clientRep.setClientId(clientId);
        clientRep.setSecret(secret);
        clientRep.setProtocol("openid-connect");
        clientRep.setClientAuthenticatorType("client-secret");
        clientRep.setPublicClient(false);
        clientRep.setStandardFlowEnabled(false);
        clientRep.setDirectAccessGrantsEnabled(false);
        clientRep.setServiceAccountsEnabled(true);

        try (var keycloak = keycloakClient()) {
            try (var result = keycloak.realm("opaca").clients().create(clientRep)) {
                if (result.getStatus() > 299) {
                    throw new IOException("Keycloak Client could not be created: " + result.getStatusInfo());
                }
            }
        }
        return secret;
    }

    public void deleteClient(String clientId) throws IOException {
        try (var keycloak = keycloakClient()) {
            var client = keycloak.realm("opaca").clients().findByClientId(clientId).stream().findAny();
            if (client.isPresent()) {
                try (var result = keycloak.realm("opaca").clients().delete(client.get().getId())) {
                    if (result.getStatus() > 299) {
                        throw new IOException("Keycloak Client could not be deleted: " + result.getStatusInfo());
                    }
                }
            } else {
                throw new NoSuchElementException("Keycloak Client not found: " + clientId);
            }
        }
    }

    public static void main(String[] args) throws Exception {

        var userToken = KeycloakUtil.getTokenForUser("http://localhost:8888", "opaca", "opaca-rp", "test", "test1234");
        System.out.println(userToken);
        KeycloakUtil.validateToken("http://localhost:8888", "opaca", userToken);

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
        clientRep.setClientAuthenticatorType("client-secret");
        clientRep.setPublicClient(false);
        clientRep.setStandardFlowEnabled(false);
        clientRep.setDirectAccessGrantsEnabled(false);
        clientRep.setServiceAccountsEnabled(true);

        var res2 = keycloak.realm("opaca").clients().create(clientRep);
        System.out.println("RESPONSE " + res2.getStatus());

        try {
            var clientToken = KeycloakUtil.getTokenForClient("http://localhost:8888", "opaca", clientId, secret);
            System.out.println("TOKEN " + clientToken);
            KeycloakUtil.validateToken("http://localhost:8888", "opaca", clientToken);
        } catch (Exception e) {
            e.printStackTrace();
        }

        var id = keycloak.realm("opaca").clients().findByClientId(clientId).get(0).getId();
        System.out.println(id);
        var res3 = keycloak.realm("opaca").clients().delete(id);
        System.out.println("RESPONSE " + res3.getStatus());
    }

    @Deprecated
    public void createTempSubUser(String user, String owner) {

    }

    @Deprecated
    public Boolean removeUser(String username) {
        return false;
    }

    @Deprecated
    public String generateToken(String owner, Duration duration) {
        return null;
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
