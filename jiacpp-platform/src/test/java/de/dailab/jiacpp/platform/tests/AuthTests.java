package de.dailab.jiacpp.platform.tests;

import de.dailab.jiacpp.model.RuntimePlatform;
import de.dailab.jiacpp.platform.Application;
import static de.dailab.jiacpp.platform.tests.TestUtils.*;

import de.dailab.jiacpp.platform.user.Privilege;
import de.dailab.jiacpp.platform.user.Role;
import de.dailab.jiacpp.platform.user.TokenUserDetailsService;
import org.junit.*;
import org.junit.runners.MethodSorters;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AuthTests {

    private static final String PLATFORM_A_PORT = "8003";
    private static final String PLATFORM_B_PORT = "8004";
    private final String PLATFORM_A = "http://localhost:" + PLATFORM_A_PORT;
    private final String PLATFORM_B = "http://localhost:" + PLATFORM_B_PORT;
    private static ConfigurableApplicationContext platformA = null;
    private static ConfigurableApplicationContext platformB = null;
    private static TokenUserDetailsService tokenUserDetailsService = null;
    private static String token_A = null;
    private static String token_B = null;
    private static String containerId = null;
    private static String containerIP = null;
    private static String containerToken = null;
    private static String platformBBaseUrl = null;


    @BeforeClass
    public static void setupPlatform() {
        platformA = SpringApplication.run(Application.class, "--server.port=" + PLATFORM_A_PORT,
                "--default_image_directory=./default-test-images", "--security.enableAuth=true",
                "--security.secret=top-secret-key-for-unit-testing",
                "--username_platform=testUser", "--password_platform=testPwd", "--role_platform=ADMIN");
        platformB = SpringApplication.run(Application.class, "--server.port=" + PLATFORM_B_PORT,
                "--default_image_directory=./default-test-images", "--security.enableAuth=true",
                "--security.secret=top-secret-key-for-unit-testing",
                "--username_platform=testUser", "--password_platform=testPwd", "--role_platform=ADMIN");
        // this is kinda ugly, should add proper functionality to add a "real" user
        tokenUserDetailsService = platformA.getBean(TokenUserDetailsService.class);
        registerAllUser();
    }

    @AfterClass
    public static void stopPlatform() {
        platformA.close();
        platformB.close();
    }


    @Test
    public void test1LoginA() throws Exception {
        var auth_A = authQuery("testUser", "testPwd");
        var con_A = request(PLATFORM_A, "POST", "/login" + auth_A, null);
        Assert.assertEquals(200, con_A.getResponseCode());
        token_A = result(con_A);
        Assert.assertNotNull(token_A);
        var auth_B = authQuery("testUser", "testPwd");
        var con_B = request(PLATFORM_B, "POST", "/login" + auth_B, null);
        Assert.assertEquals(200, con_B.getResponseCode());
        token_B = result(con_B);
        Assert.assertNotNull(token_B);
    }

    @Test
    public void test1LoginMissingAuth() throws Exception {
        var con = request(PLATFORM_A, "POST", "/login", null);
        Assert.assertEquals(400, con.getResponseCode());
    }

    @Test
    public void test1LoginWrongUser() throws Exception {
        var auth = authQuery("wrongUser", "testPwd");
        var con = request(PLATFORM_A, "POST", "/login" + auth, null);
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    public void test1LoginWrongPwd() throws Exception {
        var auth = authQuery("testUser", "wrongPwd");
        var con = request(PLATFORM_A, "POST", "/login" + auth, null);
        Assert.assertEquals(403, con.getResponseCode());
    }


    // Authentication against the Platform

    @Test
    public void test2WithToken() throws Exception {
        var con_A = requestWithToken(PLATFORM_A, "GET", "/info", null, token_A);
        Assert.assertEquals(200, con_A.getResponseCode());

        var con_B = requestWithToken(PLATFORM_B, "GET", "/info", null, token_B);
        Assert.assertEquals(200, con_B.getResponseCode());
        var info = result(con_B, RuntimePlatform.class);
        Assert.assertNotNull(info);
        platformBBaseUrl = info.getBaseUrl();
    }

    @Test
    public void test2WithWrongToken() throws Exception {
        var invalidToken = "wrong-token";
        var con = requestWithToken(PLATFORM_A, "GET", "/info", null, invalidToken);
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    public void test2WithoutToken() throws Exception {
        var con = request(PLATFORM_A, "GET", "/info", null);
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void test3Deploy() throws Exception {
        var image = getSampleContainerImage();
        var con = requestWithToken(PLATFORM_A, "POST", "/containers", image, token_A);
        Assert.assertEquals(200, con.getResponseCode());
        containerId = result(con);

        con = requestWithToken(PLATFORM_A, "GET", "/containers/" + containerId, null, token_A);
        var res = result(con, Map.class);
        var connectivity = ((Map<String, Object>) res.get("connectivity"));
        containerIP = String.format("%s:%s", connectivity.get("publicUrl"), connectivity.get("apiPortMapping"));
    }

    @Test
    public void test4GetContainerToken() throws Exception {
        var con = requestWithToken(PLATFORM_A, "POST", "/invoke/GetInfo", Map.of(), token_A);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        containerToken = (String) res.get("TOKEN");
        Assert.assertTrue(containerToken != null && ! containerToken.equals(""));

        // container token can be used to call platform routes
        con = requestWithToken(PLATFORM_A, "GET", "/info", null, containerToken);
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test4TriggerAutoNotify() throws Exception {
        // create new agent action
        var con = requestWithToken(PLATFORM_A, "POST", "/invoke/CreateAction", Map.of("name", "TestAction", "notify", "true"), token_A);
        Assert.assertEquals(200, con.getResponseCode());

        // agent container must be able to call parent platform route to notify platform of change
        con = requestWithToken(PLATFORM_A, "POST", "/invoke/TestAction", Map.of(), token_A);
        Assert.assertEquals(200, con.getResponseCode());
    }


    // Authentication against the containers

    @Test
    public void test4WithToken() throws Exception {
        var con = requestWithToken(containerIP, "GET", "/info", null, containerToken);
        Assert.assertEquals(200, con.getResponseCode());
    }
    
    @Test
    public void test4WithWrongToken() throws Exception {
        var invalidToken = "wrong-token";
        var con = requestWithToken(containerIP, "GET", "/info", null, invalidToken);
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    public void test4WithoutToken() throws Exception {
        var con = requestWithToken(containerIP, "GET", "/info", null, null);
        Assert.assertEquals(403, con.getResponseCode());
    }


     // Authentication against the connected platforms

    @Test
    public void test5ConnectPlatformWrongPwd() throws Exception {
        var auth = authQuery("testUser", "wrongPwd");
        var con = requestWithToken(PLATFORM_B, "POST", "/connections" + auth, PLATFORM_A, token_B);
        Assert.assertEquals(502, con.getResponseCode());
    }

    @Test
    public void test5ConnectPlatformWrongUser() throws Exception {
        var auth = authQuery("wrongUser", "testPwd");
        var con = requestWithToken(PLATFORM_B, "POST", "/connections" + auth, PLATFORM_A, token_B);
        Assert.assertEquals(502, con.getResponseCode());
    }

    @Test
    public void test6ConnectPlatform() throws Exception {
        var auth = authQuery("testUser", "testPwd");
        var con = requestWithToken(PLATFORM_B, "POST", "/connections" + auth, PLATFORM_A, token_B);
        Assert.assertEquals(200, con.getResponseCode());
        Assert.assertTrue(result(con, Boolean.class));
    }

    @Test
    public void test7invokeInfoAtDifferentPlatform() throws Exception {
        var con = requestWithToken(PLATFORM_B, "POST", "/invoke/GetInfo", Map.of(), token_B);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        containerToken = (String) res.get("TOKEN");
        Assert.assertTrue(containerToken != null && ! containerToken.equals(""));
    }

    // Role Authorization

    @Test
    public void test8AdminAuth() throws Exception {
        var con = requestWithToken(PLATFORM_B, "GET", "/info", null, token_A);
        Assert.assertEquals(200, con.getResponseCode());
        var info = result(con, RuntimePlatform.class);
        Assert.assertNotNull(info);
        platformBBaseUrl = info.getBaseUrl();

        con = requestWithToken(PLATFORM_A, "GET", "/connections", null, token_A);
        Assert.assertEquals(200, con.getResponseCode());

        var message = Map.of("payload", "testBroadcast", "replyTo", "User broadcast!");
        con = requestWithToken(PLATFORM_A, "POST", "/broadcast/topic", message, token_A);
        Assert.assertEquals(200, con.getResponseCode());

        var image = getSampleContainerImage();
        con = requestWithToken(PLATFORM_A, "POST", "/containers", image, token_A);
        Assert.assertEquals(200, con.getResponseCode());
        var newContainerId = result(con);

        con = requestWithToken(PLATFORM_A, "DELETE", "/containers/" + newContainerId, image, token_A);
        Assert.assertEquals(200, con.getResponseCode());

        // TODO figure out connection request with security enabled
        // con = requestWithToken(PLATFORM_A, "POST", "/connections", platformBBaseUrl, token_A);
        // Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test8ContributorAuth() throws Exception {
        var token_cont = getUserToken("contributor");
        Assert.assertNotNull(token_cont);

        var con = requestWithToken(PLATFORM_A, "GET", "/info", null, token_cont);
        Assert.assertEquals(200, con.getResponseCode());

        con = requestWithToken(PLATFORM_A, "GET", "/connections", null, token_cont);
        Assert.assertEquals(200, con.getResponseCode());

        var message = Map.of("payload", "testBroadcast", "replyTo", "User broadcast!");
        con = requestWithToken(PLATFORM_A, "POST", "/broadcast/topic", message, token_cont);
        Assert.assertEquals(200, con.getResponseCode());

        var image = getSampleContainerImage();
        con = requestWithToken(PLATFORM_A, "POST", "/containers", image, token_cont);
        Assert.assertEquals(200, con.getResponseCode());
        var newContainerId = result(con);

        con = requestWithToken(PLATFORM_A, "DELETE", "/containers/" + newContainerId, image, token_cont);
        Assert.assertEquals(200, con.getResponseCode());

        con = requestWithToken(PLATFORM_A, "POST", "/connections", platformBBaseUrl, token_cont);
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    public void test8UserAuth() throws Exception {
        var token_user = getUserToken("user");
        Assert.assertNotNull(token_user);

        var con = requestWithToken(PLATFORM_A, "GET", "/info", null, token_user);
        Assert.assertEquals(200, con.getResponseCode());

        con = requestWithToken(PLATFORM_A, "GET", "/connections", null, token_user);
        Assert.assertEquals(200, con.getResponseCode());

        var message = Map.of("payload", "testBroadcast", "replyTo", "User broadcast!");
        con = requestWithToken(PLATFORM_A, "POST", "/broadcast/topic", message, token_user);
        Assert.assertEquals(200, con.getResponseCode());

        var image = getSampleContainerImage();
        con = requestWithToken(PLATFORM_A, "POST", "/containers", image, token_user);
        Assert.assertEquals(403, con.getResponseCode());

        con = requestWithToken(PLATFORM_A, "POST", "/connections", platformBBaseUrl, token_user);
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    public void test8GuestAuth() throws Exception {
        var token_guest = getUserToken("guest");
        Assert.assertNotNull(token_guest);

        var con = requestWithToken(PLATFORM_A, "GET", "/info", null, token_guest);
        Assert.assertEquals(200, con.getResponseCode());

        con = requestWithToken(PLATFORM_A, "GET", "/agents", null, token_guest);
        Assert.assertEquals(200, con.getResponseCode());

        con = requestWithToken(PLATFORM_A, "GET", "/containers", null, token_guest);
        Assert.assertEquals(200, con.getResponseCode());

        con = requestWithToken(PLATFORM_A, "GET", "/history", null, token_guest);
        Assert.assertEquals(403, con.getResponseCode());

        var message = Map.of("payload", "testBroadcast", "replyTo", "doesnotmatter");
        con = requestWithToken(PLATFORM_A, "POST", "/broadcast/topic", message, token_guest);
        Assert.assertEquals(403, con.getResponseCode());

        var image = getSampleContainerImage();
        con = requestWithToken(PLATFORM_A, "POST", "/containers", image, token_guest);
        Assert.assertEquals(403, con.getResponseCode());

        con = requestWithToken(PLATFORM_A, "POST", "/connections", platformBBaseUrl, token_guest);
        Assert.assertEquals(403, con.getResponseCode());
    }


    // Container authorities started by users.

    @Test
    public void test9ContributorContainer() throws Exception {
        var auth = authQuery("contributor", "contributorPwd");
        var con = request(PLATFORM_A, "POST", "/login" + auth, null);
        Assert.assertEquals(200, con.getResponseCode());
        var token_cont = result(con);
        Assert.assertNotNull(token_cont);

        var image = getSampleContainerImage();
        con = requestWithToken(PLATFORM_A, "POST", "/containers", image, token_cont);
        Assert.assertEquals(200, con.getResponseCode());
        var newContainerId = result(con);

        con = requestWithToken(PLATFORM_A, "POST", "/invoke/GetInfo?containerID=" + newContainerId,
                Map.of(), token_cont);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        containerToken = (String) res.get("TOKEN");
        Assert.assertTrue(containerToken != null && ! containerToken.equals(""));

        con = requestWithToken(PLATFORM_A, "POST", "/containers", image, containerToken);
        Assert.assertEquals(200, con.getResponseCode());

        // This test does not work until container_auth equals user_auth who started container
        con = requestWithToken(PLATFORM_A, "POST", "/connections", platformBBaseUrl, containerToken);
        Assert.assertEquals(403, con.getResponseCode());
    }


    // Helper methods

    private String authQuery(String username, String password) {
        return buildQuery(Map.of("username", username, "password", password));
    }

    private static void addUser(String username, String password, String role) {
        Map<String, List<String>> userRoles = new HashMap<>();
        userRoles.put(role, Arrays.asList(role + "_PRIVILEGE"));
        tokenUserDetailsService.createUser(username, password, userRoles);
    }

    private static void registerAllUser() {
        // ROLE_GUEST
        addUser("guest", "guestPwd", "ROLE_GUEST");
        // ROLE_USER
        addUser("user", "userPwd", "ROLE_USER");
        // ROLE_CONTRIBUTOR
        addUser("contributor", "contributorPwd", "ROLE_CONTRIBUTOR");
        // ROLE_ADMIN
        addUser("admin", "adminPwd", "ROLE_ADMIN");
    }

    private String getUserToken(String userType) throws Exception {
        var auth = authQuery(userType, userType + "Pwd");
        var con = request(PLATFORM_A, "POST", "/login" + auth, null);
        return result(con);
    }

}
