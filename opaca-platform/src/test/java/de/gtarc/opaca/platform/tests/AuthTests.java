package de.gtarc.opaca.platform.tests;

import de.gtarc.opaca.model.*;
import de.gtarc.opaca.platform.Application;
import static de.gtarc.opaca.platform.tests.TestUtils.*;

import org.junit.*;
import org.junit.runners.MethodSorters;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;


/**
 * Testing different aspects of authentication, e.g. logging in with correct, incorrect or missing credentials.
 * There are some dependencies between the tests in this module, indicated by the numbers in the tests' names:
 * 1. testing login with correct or incorrect credentials
 * 2. calling another route with correct or incorrect token
 * 3. deploy a container
 * 4. invoke routes of that container with/without proper auth
 * 5. connect platform, ...
 * 6. etc.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AuthTests {

    private static final int PLATFORM_A_PORT = 8006;
    private static final int PLATFORM_B_PORT = 8007;
    private final String PLATFORM_A = "http://localhost:" + PLATFORM_A_PORT;
    private final String PLATFORM_B = "http://localhost:" + PLATFORM_B_PORT;
    private static ConfigurableApplicationContext platformA = null;
    private static ConfigurableApplicationContext platformB = null;
    private static String token_A = null;
    private static String token_B = null;
    private static String containerId = null;
    private static String containerIP = null;
    private static String containerToken = null;
    private static String platformBBaseUrl = null;


    @BeforeClass
    public static void setupPlatform() throws InterruptedException {
        startMongoDB();
        platformA = SpringApplication.run(Application.class, "--server.port=" + PLATFORM_A_PORT,
                "--default_image_directory=./default-test-images", "--security.enableAuth=true",
                "--security.secret=top-secret-key-for-unit-testing",
                "--platform_admin_user=testUser", "--platform_admin_pwd=testPwd",
                "--spring.data.mongodb.uri=mongodb://user:pass@localhost:27018/admin");
        platformB = SpringApplication.run(Application.class, "--server.port=" + PLATFORM_B_PORT,
                "--default_image_directory=./default-test-images", "--security.enableAuth=true",
                "--security.secret=top-secret-key-for-unit-testing",
                "--platform_admin_user=testUser", "--platform_admin_pwd=testPwd",
                "--spring.data.mongodb.uri=mongodb://user:pass@localhost:27018/admin");
    }

    @AfterClass
    public static void stopPlatform() {
        platformA.close();
        platformB.close();
        stopMongoDB();
    }


    @Test
    public void test01LoginA() throws Exception {
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

    @Test
    public void test01LoginMissingAuth() throws Exception {
        var con = request(PLATFORM_A, "POST", "/login", null);
        Assert.assertEquals(400, con.getResponseCode());
    }

    @Test
    public void test01LoginWrongUser() throws Exception {
        var login = createLogin("wrongUser", "testPwd");
        var con = request(PLATFORM_A, "POST", "/login", login);
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    public void test01LoginWrongPwd() throws Exception {
        var login = createLogin("testUser", "wrongPwd");
        var con = request(PLATFORM_A, "POST", "/login", login);
        Assert.assertEquals(403, con.getResponseCode());
    }


    // Authentication against the Platform

    @Test
    public void test02WithToken() throws Exception {
        var con_A = requestWithToken(PLATFORM_A, "GET", "/info", null, token_A);
        Assert.assertEquals(200, con_A.getResponseCode());

        var con_B = requestWithToken(PLATFORM_B, "GET", "/info", null, token_B);
        Assert.assertEquals(200, con_B.getResponseCode());
        var info = result(con_B, RuntimePlatform.class);
        Assert.assertNotNull(info);
        platformBBaseUrl = info.getBaseUrl();
    }

    @Test
    public void test02WithWrongToken() throws Exception {
        var invalidToken = "wrong-token";
        var con = requestWithToken(PLATFORM_A, "GET", "/info", null, invalidToken);
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    public void test02WithoutToken() throws Exception {
        var con = request(PLATFORM_A, "GET", "/info", null);
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void test03Deploy() throws Exception {
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
    public void test04GetContainerToken() throws Exception {
        var con = requestWithToken(PLATFORM_A, "POST", "/invoke/GetInfo", Map.of(), token_A);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        containerToken = (String) res.get("TOKEN");
        Assert.assertTrue(containerToken != null && ! containerToken.isEmpty());

        // container token can be used to call platform routes
        con = requestWithToken(PLATFORM_A, "GET", "/info", null, containerToken);
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test04TriggerAutoNotify() throws Exception {
        // create new agent action
        var con = requestWithToken(PLATFORM_A, "POST", "/invoke/CreateAction", Map.of("name", "TestAction", "notify", "true"), token_A);
        Assert.assertEquals(200, con.getResponseCode());

        // agent container must be able to call parent platform route to notify platform of change
        con = requestWithToken(PLATFORM_A, "POST", "/invoke/TestAction", Map.of(), token_A);
        Assert.assertEquals(200, con.getResponseCode());
    }


    // Authentication against the containers

    @Test
    public void test04WithToken() throws Exception {
        var con = requestWithToken(containerIP, "GET", "/info", null, containerToken);
        Assert.assertEquals(200, con.getResponseCode());
    }
    
    @Test
    public void test04WithWrongToken() throws Exception {
        var invalidToken = "wrong-token";
        var con = requestWithToken(containerIP, "GET", "/info", null, invalidToken);
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    public void test04WithoutToken() throws Exception {
        var con = requestWithToken(containerIP, "GET", "/info", null, null);
        Assert.assertEquals(403, con.getResponseCode());
    }


     // Authentication against the connected platforms

    @Test
    public void test05ConnectPlatformWrongPwd() throws Exception {
        var loginCon = createLoginCon("testUser", "wrongPwd", PLATFORM_A);
        var con = requestWithToken(PLATFORM_B, "POST", "/connections", loginCon, token_B);
        Assert.assertEquals(502, con.getResponseCode());
    }

    @Test
    public void test05ConnectPlatformWrongUser() throws Exception {
        var loginCon = createLoginCon("wrongUser", "testPwd", PLATFORM_A);
        var con = requestWithToken(PLATFORM_B, "POST", "/connections", loginCon, token_B);
        Assert.assertEquals(502, con.getResponseCode());
    }

    @Test
    public void test06ConnectPlatform() throws Exception {
        var loginCon = createLoginCon("testUser", "testPwd", PLATFORM_A);
        var con = requestWithToken(PLATFORM_B, "POST", "/connections", loginCon, token_B);
        Assert.assertEquals(200, con.getResponseCode());
        Assert.assertTrue(result(con, Boolean.class));
    }

    @Test
    public void test07invokeInfoAtDifferentPlatform() throws Exception {
        var con = requestWithToken(PLATFORM_B, "POST", "/invoke/GetInfo", Map.of(), token_B);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        containerToken = (String) res.get("TOKEN");
        Assert.assertTrue(containerToken != null && ! containerToken.isEmpty());
    }

    // User Management

    @Test
    public void test08GetContainerOwner() throws Exception {
        var con = requestWithToken(PLATFORM_A, "POST", "/invoke/GetInfo", Map.of(), token_A);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        System.out.println(res);
        Assert.assertEquals("testUser", res.get("OWNER"));
    }

    @Test
    public void test08AddUser() throws Exception {
        // GUEST USER
        var con = requestWithToken(PLATFORM_A, "POST", "/users",
                getUser("guest", "guestPwd", Role.GUEST, null), token_A);
        Assert.assertEquals(201, con.getResponseCode());

        // (NORMAL) USER
        con = requestWithToken(PLATFORM_A, "POST", "/users",
                getUser("user", "userPwd", Role.USER, null), token_A);
        Assert.assertEquals(201, con.getResponseCode());

        // CONTRIBUTOR USER
        con = requestWithToken(PLATFORM_A, "POST", "/users",
                getUser("contributor", "contributorPwd", Role.CONTRIBUTOR, null), token_A);
        Assert.assertEquals(201, con.getResponseCode());

        // ADMIN USER
        con = requestWithToken(PLATFORM_A, "POST", "/users",
                getUser("admin", "adminPwd", Role.ADMIN, null), token_A);
        Assert.assertEquals(201, con.getResponseCode());

        // TEST USER
        con = requestWithToken(PLATFORM_A, "POST", "/users",
                getUser("test", "testPwd", Role.GUEST, null), token_A);
        Assert.assertEquals(201, con.getResponseCode());

        // SECOND CONTRIBUTOR USER FOR SPECIFIC AUTHORITY TEST
        con = requestWithToken(PLATFORM_A, "POST", "/users",
                getUser("contributor2", "contributor2Pwd", Role.CONTRIBUTOR, null), token_A);
        Assert.assertEquals(201, con.getResponseCode());
    }

    // Role Authorization

    @Test
    public void test09AdminAuth() throws Exception {
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

        con = requestWithToken(PLATFORM_A, "DELETE", "/containers/" + containerId, image, token_A);
        Assert.assertEquals(200, con.getResponseCode());

        // TODO figure out connection request with security enabled
        // con = requestWithToken(PLATFORM_A, "POST", "/connections", platformBBaseUrl, token_A);
        // Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test09ContributorAuth() throws Exception {
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
    public void test09UserAuth() throws Exception {
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
    public void test09GuestAuth() throws Exception {
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

        con = requestWithToken(PLATFORM_A, "GET", "/users/guest", null, token_guest);
        Assert.assertEquals(200, con.getResponseCode());

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
    public void test10ContributorContainer() throws Exception {
        var token_cont = getUserToken("contributor");
        Assert.assertNotNull(token_cont);

        // Create a container with "CONTRIBUTOR" authority
        var image = getSampleContainerImage();
        var con = requestWithToken(PLATFORM_A, "POST", "/containers", image, token_cont);
        Assert.assertEquals(200, con.getResponseCode());
        var newContainerId = result(con);

        var contContainerToken = getToken(newContainerId, newContainerId);
        Assert.assertFalse(contContainerToken.isEmpty());

        // Check if container can perform actions which require "CONTRIBUTOR" role
        con = requestWithToken(PLATFORM_A, "POST", "/containers", image, contContainerToken);
        Assert.assertEquals(200, con.getResponseCode());
        var newContainerId2 = result(con);
        con = requestWithToken(PLATFORM_A, "DELETE", "/containers/" + newContainerId2, image, contContainerToken);
        Assert.assertEquals(200, con.getResponseCode());

        // Check if container can NOT perform actions which require "ADMIN" role
        con = requestWithToken(PLATFORM_A, "POST", "/users",
                getUser("forbiddenUser", "forbidden", Role.GUEST, null), contContainerToken);
        Assert.assertEquals(403, con.getResponseCode());

        // Container deletes itself
        con = requestWithToken(PLATFORM_A, "DELETE", "/containers/" + newContainerId, image, token_cont);
        Assert.assertEquals(200, con.getResponseCode());
    }

    // Specific User Authority

    @Test
    public void test11DeleteOwnContainer() throws Exception {
        var token_cont = getUserToken("contributor");
        Assert.assertNotNull(token_cont);
        var token_cont2 = getUserToken("contributor2");
        Assert.assertNotNull(token_cont);

        // Create a container with "CONTRIBUTOR" authority
        var image = getSampleContainerImage();
        var con = requestWithToken(PLATFORM_A, "POST", "/containers", image, token_cont);
        Assert.assertEquals(200, con.getResponseCode());
        var newContainerId = result(con);

        con = requestWithToken(PLATFORM_A, "DELETE", "/containers/" + newContainerId, image, token_cont2);
        Assert.assertEquals(403, con.getResponseCode());

        con = requestWithToken(PLATFORM_A, "DELETE", "/containers/" + newContainerId, image, token_cont);
        Assert.assertEquals(200, con.getResponseCode());
    }


    // Helper methods

    private Login createLogin(String username, String password) {
        return new Login(username, password);
    }

    private LoginConnection createLoginCon(String username, String password, String url) {
        return new LoginConnection(username, password, url);
    }

    private String getUserToken(String userType) throws Exception {
        return getToken(userType, userType + "Pwd");
    }

    private String getToken(String user, String password) throws Exception {
        var login = createLogin(user, password);
        var con = request(PLATFORM_A, "POST", "/login", login);
        return result(con);
    }

}
