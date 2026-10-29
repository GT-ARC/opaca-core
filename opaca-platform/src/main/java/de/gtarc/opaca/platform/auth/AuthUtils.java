package de.gtarc.opaca.platform.auth;

import de.gtarc.opaca.platform.PlatformConfig;
import de.gtarc.opaca.util.KeycloakUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides various low-level helper-methods for working with Keycloak, managing clients and access tokens,
 * getting information about the current user, etc.
 * Also for handling Container-Login tokens. This is not really related to Keycloak, but also auth, and is so simple
 * it does not really warrant a separate helper class. Could also be integrated with Keycloak tokens in the future...
 */
@Log4j2
@Service
public class AuthUtils {

    // User roles. These roles and their hierarchy have to be defined in the Keycloak realm!
    final static String ROLE_MANAGER = "MANAGER";
    final static String ROLE_CONTRIBUTOR = "CONTRIBUTOR";
    final static String ROLE_USER = "USER";
    final static String ROLE_GUEST = "GUEST";

    public final static String ANONYMOUS_USER = "anonymous";


    @Autowired
    private PlatformConfig config;

    /** platform's own UUID */
    public final String platformId = UUID.randomUUID().toString();

    /** secret for private KC client used by platform for requests to containers */
    private String platformClientSecret = null;


    /**
     * When using auth, create a Keycloak client for the Platform itself, to be used when forwarding
     * requests to deployed Agent Containers or connected Platforms.
     */
    @PostConstruct
    private void startup() {
        // some basic config consistency checks
        if (authRequired() && ! authEnabled()) {
            log.fatal("Keycloak URL must be given if Authentication is required.");
            System.exit(1);
        }
        if (authEnabled()) {
            if (! (config.isSet(config.keycloakClientId) &&
                    config.isSet(config.keycloakAdmin) &&
                    config.isSet(config.keycloakAdminPw)
            )) {
                log.fatal("When using Keycloak, KC-Client, -Admin and -Admin-PW must also be set.");
                System.exit(1);
            }
            // create client for platform itself
            for (int i = 0; i < 10; i++) {
                try {
                    platformClientSecret = createClientAndGetSecret(platformId, "OPACA RP at " + config.getOwnBaseUrl());
                    break;
                } catch (Exception e) {
                    if (i < 9) {
                        log.warn("Could not get Platform Client Secret. Retrying in 5s...");
                        try { Thread.sleep(5000); } catch (InterruptedException ignore) {}
                    } else {
                        log.fatal("Could not get Platform Client Secret after 10 tries. Is Keycloak running and the realm configured?");
                        System.exit(1);
                    }
                }
            }
        }
    }

    public boolean authRequired() {
        return config.requireAuth;
    }

    public boolean authEnabled() {
        return config.isSet(config.keycloakIssuerUri);
    }

    /**
     * Delete platform client again on shutdown.
     */
    @PreDestroy
    private void teardown() throws IOException {
        // delete platform client
        deleteClient(platformId);
    }

    /*
     * KEYCLOAK AUTHENTICATION
     */

    /**
     * Get JWT for Keycloak user, used in the /login route.
     */
    public String getTokenForUser(String username, String password) throws IOException {
        if (! authEnabled()) {
            throw new IllegalArgumentException("Login not possible: Keycloak is not configured!");
        }
        return KeycloakUtil.getTokenForUser(config.keycloakIssuerUri, config.keycloakClientId, username, password);
    }

    /**
     * Get Token for the platform itself, for requests to deployed containers and connected platforms.
     */
    public String getPlatformToken() {
        if (platformClientSecret == null) return null;
        // TODO reuse last token if still valid
        try {
            // TODO include current request user's username as extra payload?
            return KeycloakUtil.getTokenForClient(config.keycloakIssuerUri, platformId, platformClientSecret);
        } catch (IOException e) {
            throw new RuntimeException("Could not get JWT for Runtime Platform", e);
        }
    }

    /**
     * Creates a temporary private Keycloak Client, e.g. for the platform itself or a deployed container.
     * Returns the client's secret, which along with the client-id can be used to get access tokens for this client.
     */
    public String createClientAndGetSecret(String clientId, String description) throws  IOException {
        var secret = UUID.randomUUID().toString();
        KeycloakUtil.createClient(getIssuerUri(), getRealm(), getAdminToken(), clientId, description, secret);
        return secret;
    }

    /**
     * Delete temporary client, e.g. when removing containers again or stopping the platform.
     */
    public boolean deleteClient(String clientId) throws IOException {
        if (! authEnabled()) return false;
        return KeycloakUtil.deleteClient(getIssuerUri(), getRealm(), getAdminToken(), clientId);
    }

    /**
     * Get token for Keycloak admin user, for managing temporary clients via the Keycloak admin REST API.
     */
    private String getAdminToken() throws IOException {
        return KeycloakUtil.getAdminToken(getIssuerUri(), config.keycloakAdmin, config.keycloakAdminPw);
    }

    /**
     * Get the logged-in user from the user token in auth context, or default user if no auth.
     * The default-user is only relevant for container-login if no auth is enabled and only used
     * to associate the container logins with.
     */
    public String getRequestUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() instanceof Jwt jwt) {
            return jwt.getClaimAsString("preferred_username");
        } else if (! authRequired()){
            return ANONYMOUS_USER;
        } else {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public Set<String> getRequestUserRoles() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() instanceof Jwt jwt) {
            var roles = (List<String>) jwt.getClaimAsMap("realm_access").get("roles");
            return roles.stream().filter(x -> x.toUpperCase().equals(x)).collect(Collectors.toSet());
        } else if (! authRequired()){
            return Set.of(ROLE_MANAGER, ROLE_CONTRIBUTOR, ROLE_USER, ROLE_GUEST);
        } else {
            return Set.of();
        }
    }

    /**
     * Checks if the current request user is either a "manager" (formerly admin)
     * or the request user is performing request on its own data
     * @param username Name of user which will get affected by request (NOT THE CURRENT REQUEST USER)
     */
    public boolean isAdminOrSelf(String username) {
        return Objects.equals(username, getRequestUser()) || getRequestUserRoles().contains(ROLE_MANAGER);
    }

    private String getIssuerUri() {
        return config.keycloakIssuerUri.split("/realms/")[0];
    }

    private String getRealm() {
        return config.keycloakIssuerUri.split("/realms/")[1];
    }

    /*
     * HANDLING OF CONTAINER TOKENS
     * Container-Tokens were previously stored in the User objects, in the Database. Now, with Keycloak, we just
     * store the Access tokens in an internal HashMap (they are obsolete after restart anyway). Midterm, they might
     * not be needed anymore at all, if we include the original request user in the Token sent to the Container.
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
