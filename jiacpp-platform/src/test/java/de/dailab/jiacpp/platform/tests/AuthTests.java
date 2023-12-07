package de.dailab.jiacpp.platform.tests;

import de.dailab.jiacpp.model.Login;
import de.dailab.jiacpp.model.LoginConnection;
import de.dailab.jiacpp.platform.Application;
import static de.dailab.jiacpp.platform.tests.TestUtils.*;

import org.junit.*;
import org.junit.runners.MethodSorters;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AuthTests {

    private static final String PLATFORM_A_PORT = "8003";
    private static final String PLATFORM_B_PORT = "8004";
    private final String PLATFORM_A = "http://localhost:" + PLATFORM_A_PORT;
    private final String PLATFORM_B = "http://localhost:" + PLATFORM_B_PORT;

    private static ConfigurableApplicationContext platform_A = null;
    private static ConfigurableApplicationContext platform_B = null;
    private static String token_A = null;
    private static String token_B = null;
    private static String containerId = null;
    private static String containerIP = null;
    private static String containerToken = null;



    
    @BeforeClass
    public static void setupPlatform() {
        platform_A = SpringApplication.run(Application.class, "--server.port=" + PLATFORM_A_PORT,
                "--default_image_directory=./default-test-images", "--security.enableAuth=true",
                "--security.secret=top-secret-key-for-unit-testing",
                "--username_platform=testUser", "--password_platform=testPwd");
        platform_B = SpringApplication.run(Application.class, "--server.port=" + PLATFORM_B_PORT,
                "--default_image_directory=./default-test-images", "--security.enableAuth=true",
                "--security.secret=top-secret-key-for-unit-testing",
                "--username_platform=testUser", "--password_platform=testPwd");
    }

    @AfterClass
    public static void stopPlatform() {
        platform_A.close();
        platform_B.close();
    }


    @Test
    public void test1LoginA() throws Exception {
        var login_A = createLogin("testUser", "testPwd");
        var con_A = request(PLATFORM_A, "POST", "/login", login_A);
        Assert.assertEquals(200, con_A.getResponseCode());
        token_A = result(con_A);
        Assert.assertNotNull(token_A);
        var login_B = createLogin("testUser", "testPwd");
        var con_B = request(PLATFORM_B, "POST", "/login", login_B);
        Assert.assertEquals(200, con_B.getResponseCode());
        token_B = result(con_B);
        Assert.assertNotNull(token_B);
    }
    
    public void test1LoginMissingAuth() throws Exception {
        var con = request(PLATFORM_A, "POST", "/login", null);
        Assert.assertEquals(400, con.getResponseCode());
    }

    @Test
    public void test1LoginWrongUser() throws Exception {
        var login = createLogin("wrongUser", "testPwd");
        var con = request(PLATFORM_A, "POST", "/login", login);
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    public void test1LoginWrongPwd() throws Exception {
        var login = createLogin("testUser", "wrongPwd");
        var con = request(PLATFORM_A, "POST", "/login", login);
        Assert.assertEquals(403, con.getResponseCode());
    }


    // Authentication against the Platform

    @Test
    public void test2WithToken() throws Exception {
        var con_A = requestWithToken(PLATFORM_A, "GET", "/info", null, token_A);
        Assert.assertEquals(200, con_A.getResponseCode());
        var con_B = requestWithToken(PLATFORM_B, "GET", "/info", null, token_B);
        Assert.assertEquals(200, con_B.getResponseCode());
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
        var loginCon = createLoginCon("testUser", "wrongPwd", PLATFORM_A);
        var con = requestWithToken(PLATFORM_B, "POST", "/connections", loginCon, token_B);
        Assert.assertEquals(502, con.getResponseCode());
    }

    @Test
    public void test5ConnectPlatformWrongUser() throws Exception {
        var loginCon = createLoginCon("wrongUser", "testPwd", PLATFORM_A);
        var con = requestWithToken(PLATFORM_B, "POST", "/connections", loginCon, token_B);
        Assert.assertEquals(502, con.getResponseCode());
    }

    @Test
    public void test6ConnectPlatform() throws Exception {
        var loginCon = createLoginCon("testUser", "testPwd", PLATFORM_A);
        var con = requestWithToken(PLATFORM_B, "POST", "/connections", loginCon, token_B);
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

    // Helper methods

    private Login createLogin(String username, String password) {
        return new Login(username, password);
    }

    private LoginConnection createLoginCon(String username, String password, String url) {
        return new LoginConnection(username, password, url);
    }

}
