package de.dailab.jiacpp.platform.tests;

import de.dailab.jiacpp.model.RuntimePlatform;
import de.dailab.jiacpp.platform.Application;
import static de.dailab.jiacpp.platform.tests.TestUtils.*;

import de.dailab.jiacpp.platform.auth.Role;
import de.dailab.jiacpp.platform.auth.TokenUserDetailsService;
import org.junit.*;
import org.junit.runners.MethodSorters;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;
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
    private static String token = null;
    private static String containerId = null;
    private static String containerIP = null;
    private static String containerToken = null;
    private static String platformBBaseUrl = null;


    @BeforeClass
    public static void setupPlatform() {
        platformA = SpringApplication.run(Application.class, "--server.port=" + PLATFORM_A_PORT,
                "--default_image_directory=./default-test-images", "--security.enableAuth=true",
                "--security.secret=top-secret-key-for-unit-testing",
                "--username_platform=testUser", "--password_platform=testPwd",
                "--role_platform=ADMIN");
        platformB = SpringApplication.run(Application.class, "--server.port=" + PLATFORM_B_PORT);
        // this is kinda ugly, should add proper functionality to add a "real" user
        tokenUserDetailsService = platformA.getBean(TokenUserDetailsService.class);
    }

    @AfterClass
    public static void stopPlatform() {
        platformA.close();
    }


    @Test
    public void test1Login() throws Exception {
        var auth = authQuery("testUser", "testPwd");
        var con = request(PLATFORM_A, "POST", "/login" + auth, null);
        Assert.assertEquals(200, con.getResponseCode());
        token = result(con);
        Assert.assertNotNull(token);
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
        var con = requestWithToken(PLATFORM_A, "GET", "/info", null, token);
        Assert.assertEquals(200, con.getResponseCode());
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
        var con = requestWithToken(PLATFORM_A, "POST", "/containers", image, token);
        Assert.assertEquals(200, con.getResponseCode());
        containerId = result(con);

        con = requestWithToken(PLATFORM_A, "GET", "/containers/" + containerId, null, token);
        var res = result(con, Map.class);
        var connectivity = ((Map<String, Object>) res.get("connectivity"));
        containerIP = String.format("%s:%s", connectivity.get("publicUrl"), connectivity.get("apiPortMapping"));
    }

    @Test
    public void test4GetContainerToken() throws Exception {
        var con = requestWithToken(PLATFORM_A, "POST", "/invoke/GetInfo", Map.of(), token);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        containerToken = (String) res.get("TOKEN");
        Assert.assertTrue(containerToken != null && ! containerToken.equals(""));

        // container token can be used to call platform routes
        con = requestWithToken(PLATFORM_A, "GET", "/info", null, containerToken);
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test7TriggerAutoNotify() throws Exception {
        // create new agent action
        var con = requestWithToken(PLATFORM_A, "POST", "/invoke/CreateAction", Map.of("name", "TestAction", "notify", "true"), token);
        Assert.assertEquals(200, con.getResponseCode());

        // agent container must be able to call parent platform route to notify platform of change
        con = requestWithToken(PLATFORM_A, "POST", "/invoke/TestAction", Map.of(), token);
        Assert.assertEquals(200, con.getResponseCode());
    }


    // Authentication against the containers

    @Test
    public void test8WithToken() throws Exception {
        var con = requestWithToken(containerIP, "GET", "/info", null, containerToken);
        Assert.assertEquals(200, con.getResponseCode());
    }
    
    @Test
    public void test8WithWrongToken() throws Exception {
        var invalidToken = "wrong-token";
        var con = requestWithToken(containerIP, "GET", "/info", null, invalidToken);
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    public void test8WithoutToken() throws Exception {
        var con = requestWithToken(containerIP, "GET", "/info", null, null);
        Assert.assertEquals(403, con.getResponseCode());
    }

    // Role Authorization

    @Test
    public void test9AdminAuth() throws Exception {
        var con = request(PLATFORM_B, "GET", "/info", null);
        Assert.assertEquals(200, con.getResponseCode());
        var info = result(con, RuntimePlatform.class);
        Assert.assertNotNull(info);
        platformBBaseUrl = info.getBaseUrl();

        con = requestWithToken(PLATFORM_A, "GET", "/connections", null, token);
        Assert.assertEquals(200, con.getResponseCode());

        var message = Map.of("payload", "testBroadcast", "replyTo", "User broadcast!");
        con = requestWithToken(PLATFORM_A, "POST", "/broadcast/topic", message, token);
        Assert.assertEquals(200, con.getResponseCode());

        var image = getSampleContainerImage();
        con = requestWithToken(PLATFORM_A, "POST", "/containers", image, token);
        Assert.assertEquals(200, con.getResponseCode());
        var newContainerId = result(con);

        con = requestWithToken(PLATFORM_A, "DELETE", "/containers/" + newContainerId, image, token);
        Assert.assertEquals(200, con.getResponseCode());

        // TODO figure out connection request with security enabled
        // con = requestWithToken(PLATFORM_A, "POST", "/connections", platformBBaseUrl, token);
        // Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test9ContributorAuth() throws Exception {
        // Create new user with ROLE_USER and get token
        this.addUser("contributor", "contributorPwd", "ROLE_CONTRIBUTOR");
        var auth = authQuery("contributor", "contributorPwd");
        var con = request(PLATFORM_A, "POST", "/login" + auth, null);
        Assert.assertEquals(200, con.getResponseCode());
        token = result(con);
        Assert.assertNotNull(token);

        con = requestWithToken(PLATFORM_A, "GET", "/info", null, token);
        Assert.assertEquals(200, con.getResponseCode());

        con = requestWithToken(PLATFORM_A, "GET", "/connections", null, token);
        Assert.assertEquals(200, con.getResponseCode());

        var message = Map.of("payload", "testBroadcast", "replyTo", "User broadcast!");
        con = requestWithToken(PLATFORM_A, "POST", "/broadcast/topic", message, token);
        Assert.assertEquals(200, con.getResponseCode());

        var image = getSampleContainerImage();
        con = requestWithToken(PLATFORM_A, "POST", "/containers", image, token);
        Assert.assertEquals(200, con.getResponseCode());
        var newContainerId = result(con);

        con = requestWithToken(PLATFORM_A, "DELETE", "/containers/" + newContainerId, image, token);
        Assert.assertEquals(200, con.getResponseCode());

        con = requestWithToken(PLATFORM_A, "POST", "/connections", platformBBaseUrl, token);
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    public void test9UserAuth() throws Exception {
        // Create new user with ROLE_USER and get token
        this.addUser("user", "userPwd", "ROLE_USER");
        var auth = authQuery("user", "userPwd");
        var con = request(PLATFORM_A, "POST", "/login" + auth, null);
        Assert.assertEquals(200, con.getResponseCode());
        token = result(con);
        Assert.assertNotNull(token);

        con = requestWithToken(PLATFORM_A, "GET", "/info", null, token);
        Assert.assertEquals(200, con.getResponseCode());

        con = requestWithToken(PLATFORM_A, "GET", "/connections", null, token);
        Assert.assertEquals(200, con.getResponseCode());

        var message = Map.of("payload", "testBroadcast", "replyTo", "User broadcast!");
        con = requestWithToken(PLATFORM_A, "POST", "/broadcast/topic", message, token);
        Assert.assertEquals(200, con.getResponseCode());

        var image = getSampleContainerImage();
        con = requestWithToken(PLATFORM_A, "POST", "/containers", image, token);
        Assert.assertEquals(403, con.getResponseCode());

        con = requestWithToken(PLATFORM_A, "POST", "/connections", platformBBaseUrl, token);
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    public void test9GuestAuth() throws Exception {
        // Create new guest user with ROLE_GUEST and get token
        this.addUser("guest", "guestPwd", "ROLE_GUEST");
        var auth = authQuery("guest", "guestPwd");
        var con = request(PLATFORM_A, "POST", "/login" + auth, null);
        Assert.assertEquals(200, con.getResponseCode());
        token = result(con);
        Assert.assertNotNull(token);

        con = requestWithToken(PLATFORM_A, "GET", "/info", null, token);
        Assert.assertEquals(200, con.getResponseCode());

        con = requestWithToken(PLATFORM_A, "GET", "/agents", null, token);
        Assert.assertEquals(200, con.getResponseCode());

        con = requestWithToken(PLATFORM_A, "GET", "/containers", null, token);
        Assert.assertEquals(200, con.getResponseCode());

        con = requestWithToken(PLATFORM_A, "GET", "/history", null, token);
        Assert.assertEquals(403, con.getResponseCode());

        var message = Map.of("payload", "testBroadcast", "replyTo", "doesnotmatter");
        con = requestWithToken(PLATFORM_A, "POST", "/broadcast/topic", message, token);
        Assert.assertEquals(403, con.getResponseCode());

        var image = getSampleContainerImage();
        con = requestWithToken(PLATFORM_A, "POST", "/containers", image, token);
        Assert.assertEquals(403, con.getResponseCode());

        con = requestWithToken(PLATFORM_A, "POST", "/connections", platformBBaseUrl, token);
        Assert.assertEquals(403, con.getResponseCode());
    }


    // Helper methods

    private String authQuery(String username, String password) {
        return buildQuery(Map.of("username", username, "password", password));
    }

    private void addUser(String username, String password, String role) {
        tokenUserDetailsService.addUser(username, password, Arrays.asList(new Role(role)));
    }

}
