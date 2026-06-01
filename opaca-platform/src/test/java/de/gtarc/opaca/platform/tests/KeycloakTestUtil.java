package de.gtarc.opaca.platform.tests;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.ws.rs.core.Response;
import lombok.extern.log4j.Log4j2;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.*;

import java.util.List;

/**
 * Helper for setting up Keycloak for unit tests. There are two supported modes: Using an already running and configured
 * Keycloak instance at TEST_KC_URL, or spinning up a new Keycloak container automatically and setting it up with the
 * required realm, client, roles, and test users.
 *
 * The latter is useful for CI, but it takes some time to start the Keycloak container, so for running the unit tests
 * locally, it's recommended to start the Docker Compose in the /keycloak directory and running the Python setup script.
 */
@Log4j2
public class KeycloakTestUtil {

    public static final int TEST_KC_PORT = 9000;
    public static final String TEST_KC_URL = "http://localhost:" + TEST_KC_PORT;
    public static final String TEST_KC_USR = "admin";
    public static final String TEST_KC_PWD = "admin";

    static KeycloakContainer kc;

    public static void main(String[] args) throws Exception {
        int port = startKeycloak();
        System.out.println(port);

        Thread.sleep(3*60000);

        System.exit(0);
    }

    static Keycloak getClient(String serverUrl) {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm("master")
                .clientId("admin-cli")
                .grantType("password")
                .username(TEST_KC_USR)
                .password(TEST_KC_PWD)
                .build();
    }

    static int startKeycloak() {
        try {
            log.info("Checking whether KeyCloak is running...");
            var client = getClient(TEST_KC_URL);
            client.serverInfo().getInfo();
            return TEST_KC_PORT;
        } catch (Exception e) {
            log.warn("Testing Keycloak instance not found at " + TEST_KC_URL);
            log.warn("Starting Keycloak test container. This may take a while.");
            log.warn("For faster local testing, use the Docker Compose and setup script in /keycloak directory.");

            kc = new KeycloakContainer()
                    .withAdminUsername(TEST_KC_USR)
                    .withAdminPassword(TEST_KC_PWD);
            kc.start();
            setupTestEnvironment(kc.getAuthServerUrl());
            log.info("Started Keycloak test container at port {}", kc.getHttpPort());
            return kc.getHttpPort();
        }
    }

    static void stopKeycloak() {
        if (kc != null) {
            kc.stop();
        }
    }

    static void setupTestEnvironment(String serverUrl) {
        Keycloak client = getClient(serverUrl);

        log.info("creating realm and client...");
        var realm = createRealm(client, "opaca");
        createClient(realm, "opaca-rp");

        log.info("creating roles");
        createRole(realm, "MANAGER");
        createRole(realm, "CONTRIBUTOR");
        createRole(realm, "USER");
        createRole(realm, "GUEST");

        log.info("creating test users...");
        createUser(realm, "guest", "12345", "GUEST");
        createUser(realm, "user1", "12345", "USER");
        createUser(realm, "user2", "12345", "USER");
        createUser(realm, "contributor1", "12345", "CONTRIBUTOR");
        createUser(realm, "contributor2", "12345", "CONTRIBUTOR");
        createUser(realm, "manager", "12345", "MANAGER");

        log.info("done");
    }

    static RealmResource createRealm(Keycloak client, String name) {
        var obj = new RealmRepresentation();
        obj.setRealm(name);
        obj.setEnabled(true);
        client.realms().create(obj);
        return client.realm(name);
    }

    static void createClient(RealmResource realm, String clientId) {
        var obj = new ClientRepresentation();
        obj.setClientId(clientId);
        obj.setEnabled(true);
        obj.setProtocol("openid-connect");
        obj.setPublicClient(true);
        realm.clients().create(obj);
    }

    static void createRole(RealmResource realm, String name) {
        var obj = new RoleRepresentation();
        obj.setName(name);
        realm.roles().create(obj);
    }

    static void createUser(RealmResource realm, String username, String pwd, String role) {
        var creds = new CredentialRepresentation();
        creds.setType("password");
        creds.setValue(pwd);
        var obj = new UserRepresentation();
        obj.setUsername(username);
        obj.setEnabled(true);
        obj.setEmail(username + "@example.com");
        obj.setFirstName(username);
        obj.setLastName(username);
        obj.setCredentials(List.of(creds));

        try (Response res = realm.users().create(obj)) {
            String userId = CreatedResponseUtil.getCreatedId(res);
            if (role != null) {
                RoleRepresentation roleRep = realm.roles().get(role).toRepresentation();
                realm.users().get(userId).roles().realmLevel().add(List.of(roleRep));
            }
        }
    }

}
