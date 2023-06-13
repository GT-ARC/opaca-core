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

    @BeforeClass
    public static void setupPlatform() {
        platform = SpringApplication.run(Application.class, "--server.port=" + PLATFORM_PORT,
                "--default_image_directory=./default-test-images", "--security.enableAuth=true",
                "--security.secret=top-secret-key-for-unit-testing",
                "--username_platform=testUser", "--password_platform=testPwd");
    }

    @AfterClass
    public static void stopPlatform() throws Exception {
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
    public void test3Deploy() throws Exception {
        var image = getSampleContainerImage();
        var con = requestWithToken(PLATFORM, "POST", "/containers", image, token);
        Assert.assertEquals(200, con.getResponseCode());
        containerId = result(con);
    }

    @Test
    public void test4GetContainerToken() throws Exception {
        var con = requestWithToken(PLATFORM, "POST", "/invoke/GetInfo", Map.of(), token);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        var containerToken = (String) res.get("TOKEN");
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

    private String authQuery(String username, String password) {
        return buildQuery(Map.of("username", username, "password", password));
    }

}
