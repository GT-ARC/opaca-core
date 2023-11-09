package de.dailab.jiacpp.platform.tests;

import de.dailab.jiacpp.platform.Application;
import static de.dailab.jiacpp.platform.tests.TestUtils.*;

import org.junit.*;
import org.junit.runners.MethodSorters;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AuthTests {

    private static final String PLATFORM_PORT = "8003";
    private final String PLATFORM = "http://localhost:" + PLATFORM_PORT;

    private static ConfigurableApplicationContext platform = null;
    private static String token = null;
    private static String containerId = null;
    private static String containerIP = null;
    private static String containerToken = null;

    @BeforeClass
    public static void setupPlatform() {
        platform = SpringApplication.run(Application.class, "--server.port=" + PLATFORM_PORT,
                "--default_image_directory=./default-test-images", "--security.enableAuth=true",
                "--security.secret=top-secret-key-for-unit-testing",
                "--username_platform=testUser", "--password_platform=testPwd",
                "--role_platform=ADMIN");
    }

    @AfterClass
    public static void stopPlatform() {
        platform.close();
    }


    @Test
    public void test1Login() throws Exception {
        var auth = authQuery("testUser", "testPwd");
        var con = request(PLATFORM, "POST", "/login" + auth, null);
        Assert.assertEquals(200, con.getResponseCode());
        token = result(con);
        Assert.assertNotNull(token);
    }

    @Test
    public void test1LoginMissingAuth() throws Exception {
        var con = request(PLATFORM, "POST", "/login", null);
        Assert.assertEquals(400, con.getResponseCode());
    }

    @Test
    public void test1LoginWrongUser() throws Exception {
        var auth = authQuery("wrongUser", "testPwd");
        var con = request(PLATFORM, "POST", "/login" + auth, null);
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    public void test1LoginWrongPwd() throws Exception {
        var auth = authQuery("testUser", "wrongPwd");
        var con = request(PLATFORM, "POST", "/login" + auth, null);
        Assert.assertEquals(403, con.getResponseCode());
    }


    // Authentication against the Platform

    @Test
    public void test2WithToken() throws Exception {
        var con = requestWithToken(PLATFORM, "GET", "/info", null, token);
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test2WithWrongToken() throws Exception {
        var invalidToken = "wrong-token";
        var con = requestWithToken(PLATFORM, "GET", "/info", null, invalidToken);
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    public void test2WithoutToken() throws Exception {
        var con = request(PLATFORM, "GET", "/info", null);
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void test3Deploy() throws Exception {
        var image = getSampleContainerImage();
        var con = requestWithToken(PLATFORM, "POST", "/containers", image, token);
        Assert.assertEquals(200, con.getResponseCode());
        containerId = result(con);

        con = requestWithToken(PLATFORM, "GET", "/containers/" + containerId, null, token);
        var res = result(con, Map.class);
        var connectivity = ((Map<String, Object>) res.get("connectivity"));
        containerIP = String.format("%s:%s", connectivity.get("publicUrl"), connectivity.get("apiPortMapping"));
    }

    @Test
    public void test4GetContainerToken() throws Exception {
        var con = requestWithToken(PLATFORM, "POST", "/invoke/GetInfo", Map.of(), token);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        containerToken = (String) res.get("TOKEN");
        Assert.assertTrue(containerToken != null && ! containerToken.equals(""));

        // container token can be used to call platform routes
        con = requestWithToken(PLATFORM, "GET", "/info", null, containerToken);
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test7TriggerAutoNotify() throws Exception {
        // create new agent action
        var con = requestWithToken(PLATFORM, "POST", "/invoke/CreateAction", Map.of("name", "TestAction", "notify", "true"), token);
        Assert.assertEquals(200, con.getResponseCode());

        // agent container must be able to call parent platform route to notify platform of change
        con = requestWithToken(PLATFORM, "POST", "/invoke/TestAction", Map.of(), token);
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

    @Test
    public void test9AdminActionSuccess() throws Exception {
        // create new agent action
        var con = requestWithToken(PLATFORM, "POST", "/invoke/CreateAction", Map.of("name", "TestActionAdmin", "notify", "true"), token);
        Assert.assertEquals(200, con.getResponseCode());

        con = requestWithToken(PLATFORM, "POST", "/invoke/TestActionAdmin", Map.of(), token);
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test9GodActionFailure() throws Exception {
        // create new agent action
        var con = requestWithToken(PLATFORM, "POST", "/invoke/CreateAction", Map.of("name", "TestActionGod", "notify", "true"), token);
        Assert.assertEquals(200, con.getResponseCode());

        con = requestWithToken(PLATFORM, "POST", "/invoke/TestActionGod", Map.of(), token);
        Assert.assertEquals(403, con.getResponseCode());
    }


    // Helper methods

    private String authQuery(String username, String password) {
        return buildQuery(Map.of("username", username, "password", password));
    }

}
