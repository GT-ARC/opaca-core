package de.gtarc.opaca.platform.auth;

import de.gtarc.opaca.platform.PlatformConfig;
import de.gtarc.opaca.util.KeycloakUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.ClientRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class AuthUtils {

    final static String ROLE_ADMIN = "ADMIN";
    final static String ROLE_CONTRIBUTOR = "CONTRIBUTOR";
    final static String ROLE_USER = "USER";
    final static String ROLE_GUEST = "GUEST";


    @Autowired
    private PlatformConfig config;

    /** platform's own UUID */
    public final String platformId = UUID.randomUUID().toString();

    /** secret for private KC client used by platform for requests to containers */
    private String platformClientSecret = null;


    @PostConstruct
    private void startup() throws IOException {
        // some basic config consistency checks
        if (config.requireAuth && ! config.isSet(config.keycloakIssuerUri)) {
            System.err.println("Keycloak URL must be given if Authentication is required.");
            System.exit(1);
        }
        if (config.isSet(config.keycloakIssuerUri) && ! (
                config.isSet(config.keycloakClientId) &&
                config.isSet(config.keycloakAdmin) &&
                config.isSet(config.keycloakAdminPw)
        )) {
            System.err.println("When using Keycloak, KC-Client, -Admin and -Admin-PW must also be set.");
            System.exit(1);
        }
        // create client for platform itself
        if (config.isSet((config.keycloakIssuerUri))) {
            platformClientSecret = createClientAndGetSecret(platformId);
        }
    }

    @PreDestroy
    private void teardown() throws IOException {
        // delete platform client
        deleteClient(platformId);
    }

    /*
     * KEYCLOAK AUTHENTICATION
     */

    private Keycloak keycloakClient() {
        return KeycloakBuilder.builder()
                .serverUrl(config.keycloakIssuerUri.split("/realms/")[0])
                .realm("master")
                .clientId("admin-cli")
                .grantType("password")
                .username(config.keycloakAdmin)
                .password(config.keycloakAdminPw)
                .build();
    }

    // used for platform login
    public String getTokenForUser(String username, String password) throws IOException {
        if (config.keycloakIssuerUri == null) {
            throw new IllegalArgumentException("Login not possible: Keycloak is not configured!");
        }
        return KeycloakUtil.getTokenForUser(config.keycloakIssuerUri, config.keycloakClientId, username, password);
    }

    public String getPlatformToken() {
        if (platformClientSecret == null) return null;
        try {
            // TODO include current request user's username as extra payload?
            return KeycloakUtil.getTokenForClient(config.keycloakIssuerUri, platformId, platformClientSecret);
        } catch (IOException e) {
            throw new RuntimeException("Could not get JWT for Runtime Platform", e);
        }
    }

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

    public boolean deleteClient(String clientId) throws IOException {
        if (config.keycloakIssuerUri == null) return false;
        try (var keycloak = keycloakClient()) {
            var client = keycloak.realm("opaca").clients().findByClientId(clientId).stream().findAny();
            if (client.isPresent()) {
                try (var result = keycloak.realm("opaca").clients().delete(client.get().getId())) {
                    if (result.getStatus() > 299) {
                        throw new IOException("Keycloak Client could not be deleted: " + result.getStatusInfo());
                    }
                }
                return true;
            } else {
                return false;
            }
        }
    }

    public static void main(String[] args) throws Exception {

        var userToken = KeycloakUtil.getTokenForUser("http://localhost:8888/realms/opaca", "opaca-rp", "test", "test1234");
        System.out.println(userToken);
        KeycloakUtil.validateToken("http://localhost:8888/realms/opaca", userToken);

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
            var clientToken = KeycloakUtil.getTokenForClient("http://localhost:8888/realms/opaca", clientId, secret);
            System.out.println("TOKEN " + clientToken);
            KeycloakUtil.validateToken("http://localhost:8888/realms/opaca", clientToken);
        } catch (Exception e) {
            e.printStackTrace();
        }

        var id = keycloak.realm("opaca").clients().findByClientId(clientId).get(0).getId();
        System.out.println(id);
        var res3 = keycloak.realm("opaca").clients().delete(id);
        System.out.println("RESPONSE " + res3.getStatus());
    }


    /**
     * Get the logged-in user from the user token in auth context, or default user if no auth.
     * The default-user is only relevant for container-login if no auth is enabled and only used
     * to associate the container logins with.
     */
    public String getRequestUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth.getCredentials() instanceof Jwt jwt) {
            System.out.println("TOKEN " + jwt.getTokenValue());
            System.out.println("CLAIMS " + jwt.getClaims());
            return jwt.getClaimAsString("preferred_username");
        } else if (! config.requireAuth){
            return "anonymous";
        } else {
            return null;
        }
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
