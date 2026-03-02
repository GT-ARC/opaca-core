package de.gtarc.opaca.platform.tests;

import de.gtarc.opaca.api.AgentContainerApi;
import de.gtarc.opaca.model.*;
import de.gtarc.opaca.model.User.Role;
import de.gtarc.opaca.platform.Application;
import de.gtarc.opaca.util.WebSocketConnector;

import static de.gtarc.opaca.platform.tests.TestUtils.*;

import org.junit.*;
import org.junit.rules.TestName;
import org.junit.runners.MethodSorters;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    public static void setupPlatform() {
        platformA = SpringApplication.run(Application.class, "--server.port=" + PLATFORM_A_PORT,
                "--default_image_directory=./default-test-images", "--security.requireAuth=true",
                "--security.secret=top-secret-key-for-unit-testing",
                "--platform_admin_user=testUser", "--platform_admin_pwd=testPwd",
                "--db_embed=true");
        platformB = SpringApplication.run(Application.class, "--server.port=" + PLATFORM_B_PORT,
                "--default_image_directory=./default-test-images", "--security.requireAuth=true",
                "--security.secret=top-secret-key-for-unit-testing",
                "--platform_admin_user=testUser", "--platform_admin_pwd=testPwd",
                "--db_embed=true");
    }

    @AfterClass
    public static void stopPlatform() {
        platformA.close();
        platformB.close();
    }

    @Rule
    public TestName testName = new TestName();

    @Before
    public void printTest() {
        System.out.println(">>> RUNNING TEST AuthTests." + testName.getMethodName());
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
        Assert.assertEquals(401, con.getResponseCode());
    }

    @Test
    public void test02WithoutToken() throws Exception {
        var con = request(PLATFORM_A, "GET", "/info", null);
        Assert.assertEquals(401, con.getResponseCode());
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

    // Test basic Invoke & Stream stuff with Auth (analogous to basic Container tests)

    @Test
    public void test04InvokeAction() throws Exception {
        var con = requestWithToken(PLATFORM_A, "POST", "/invoke/GetInfo", Map.of(), token_A);
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test04GetStream() throws Exception {
        var con = requestWithToken(PLATFORM_A, "GET", "/stream/GetStream", null, token_A);
        Assert.assertEquals(200, con.getResponseCode());
        var inputStream = con.getInputStream();
        var bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        var response = bufferedReader.readLine();
        Assert.assertEquals("{\"key\":\"value\"}", response);
    }

    @Test
    public void test04PostStream() throws Exception {
        String jsonInput = "{\n  \"key\": \"value\"\n}";
        byte[] jsonData = jsonInput.getBytes(StandardCharsets.UTF_8);
        var responseCode = streamRequestWithToken(PLATFORM_A, "POST", "/stream/PostStream/sample1", jsonData, token_A);
        Assert.assertEquals(200, responseCode);

        var con = requestWithToken(PLATFORM_A, "POST", "/invoke/GetInfo/sample1", Map.of(), token_A);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        Assert.assertEquals(jsonInput, res.get("lastPostedStream"));
    }

    // Test more complex stuff, Auto-Notify etc.

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
    public void test04OutboundInvoke() throws Exception {
        // container can call action of another agent by calling /invoke at its parent platform
        var con = requestWithToken(PLATFORM_A, "POST", "/invoke/OutboundInvokeTest/sample1", Map.of("agentId", "sample2"), token_A);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, String.class);
        Assert.assertTrue(res.contains("65"));
    }

    @Test
    public void test04TriggerAutoNotify() throws Exception {
        // create new agent action
        var con = requestWithToken(PLATFORM_A, "POST", "/invoke/CreateAction", Map.of("name", "TestAction", "notify", true), token_A);
        Assert.assertEquals(200, con.getResponseCode());
        Thread.sleep(500);

        // agent container must be able to call parent platform route to notify platform of change
        con = requestWithToken(PLATFORM_A, "POST", "/invoke/TestAction", Map.of(), token_A);
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test04WebSocketEvents() throws Exception {
        // create web socket listener and collect messages
        var without_auth = new ArrayList<String>();
        WebSocketConnector.subscribe(PLATFORM_A, null, "/invoke", without_auth::add);
        var with_auth = new ArrayList<String>();
        WebSocketConnector.subscribe(PLATFORM_A, token_A, "/invoke", with_auth::add);

        // make sure connection is established first
        Thread.sleep(1000);

        // trigger different events
        var res = requestWithToken(PLATFORM_A, "POST", "/invoke/GetInfo", Map.of(), token_A).getResponseCode();
        Assert.assertEquals(200, res);
        Thread.sleep(200);

        // check that correct events have been received
        Assert.assertEquals(0, without_auth.size());
        Assert.assertEquals(1, with_auth.size());
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
    public void test05ConnectPlatformWrongToken() throws Exception {
        var loginCon = new ConnectionRequest(PLATFORM_A, false, "wrong-token");
        var con = requestWithToken(PLATFORM_B, "POST", "/connections", loginCon, token_B);
        Assert.assertEquals(502, con.getResponseCode());
    }

    @Test
    public void test06ConnectPlatform() throws Exception {
        var loginCon = new ConnectionRequest(PLATFORM_A, true, token_A);
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
                user("guest", "guestPwd", Role.GUEST), token_A);
        Assert.assertEquals(201, con.getResponseCode());

        // (NORMAL) USER
        con = requestWithToken(PLATFORM_A, "POST", "/users",
                user("user", "userPwd", Role.USER), token_A);
        Assert.assertEquals(201, con.getResponseCode());

        // CONTRIBUTOR USER
        con = requestWithToken(PLATFORM_A, "POST", "/users",
                user("contributor", "contributorPwd", Role.CONTRIBUTOR), token_A);
        Assert.assertEquals(201, con.getResponseCode());

        // ADMIN USER
        con = requestWithToken(PLATFORM_A, "POST", "/users",
                user("admin", "adminPwd", Role.ADMIN), token_A);
        Assert.assertEquals(201, con.getResponseCode());

        // TEST USER
        con = requestWithToken(PLATFORM_A, "POST", "/users",
                user("test", "testPwd", Role.GUEST), token_A);
        Assert.assertEquals(201, con.getResponseCode());

        // SECOND CONTRIBUTOR USER FOR SPECIFIC AUTHORITY TEST
        con = requestWithToken(PLATFORM_A, "POST", "/users",
                user("contributor2", "contributor2Pwd", Role.CONTRIBUTOR), token_A);
        Assert.assertEquals(201, con.getResponseCode());
    }

    @Test
    public void test08ContainerLogin() throws Exception {
        // create two users for testing
        result(requestWithToken(PLATFORM_A, "POST", "/users", user("user1", "12345", Role.USER), token_A));
        result(requestWithToken(PLATFORM_A, "POST", "/users", user("user2", "12345", Role.USER), token_A));
        var token1 = result(request(PLATFORM_A, "POST", "/login", new Login("user1", "12345")));
        var token2 = result(request(PLATFORM_A, "POST", "/login", new Login("user2", "12345")));

        // login to container as both users
        result(requestWithToken(PLATFORM_A, "POST", "/containers/login/" + containerId, new Login("container user 1", ""), token1));
        result(requestWithToken(PLATFORM_A, "POST", "/containers/login/" + containerId, new Login("container user 2", ""), token2));

        // call test-login route with different users --> container should recognize the calling user, if logged in
        var con = requestWithToken(PLATFORM_A, "POST", "/invoke/LoginTest", Map.of(), token_A);
        Assert.assertEquals(200, con.getResponseCode());
        Assert.assertEquals("\"Not logged in\"", result(con));

        con = requestWithToken(PLATFORM_A, "POST", "/invoke/LoginTest", Map.of(), token1);
        Assert.assertEquals(200, con.getResponseCode());
        Assert.assertEquals("\"Logged in as container user 1\"", result(con));

        con = requestWithToken(PLATFORM_A, "POST", "/invoke/LoginTest", Map.of(), token2);
        Assert.assertEquals(200, con.getResponseCode());
        Assert.assertEquals("\"Logged in as container user 2\"", result(con));

        // logout as user 1; user 2 still logged in
        result(requestWithToken(PLATFORM_A, "POST", "/containers/logout/" + containerId, null, token1));

        con = requestWithToken(PLATFORM_A, "POST", "/invoke/LoginTest", Map.of(), token1);
        Assert.assertEquals(200, con.getResponseCode());
        Assert.assertEquals("\"Not logged in\"", result(con));

        con = requestWithToken(PLATFORM_A, "POST", "/invoke/LoginTest", Map.of(), token2);
        Assert.assertEquals(200, con.getResponseCode());
        Assert.assertEquals("\"Logged in as container user 2\"", result(con));

        // finally, login to unknown container...
        con = requestWithToken(PLATFORM_A, "POST", "/containers/login/does-not-exist", new Login("container user 1", ""), token1);
        Assert.assertEquals(404, con.getResponseCode());
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
        Thread.sleep(500);

        // get the container's token to "impersonate" it in the next requests
        var query = buildQuery(Map.of("containerId", newContainerId));
        con = requestWithToken(PLATFORM_A, "POST", "/invoke/GetInfo" + query, Map.of(), token_cont);
        Assert.assertEquals(200, con.getResponseCode());
        var contContainerToken = (String) result(con, Map.class).get(AgentContainerApi.ENV_TOKEN);
        Assert.assertFalse(contContainerToken.isEmpty());
        Thread.sleep(500);

        // Check if container can perform actions which require "CONTRIBUTOR" role
        con = requestWithToken(PLATFORM_A, "POST", "/containers", image, contContainerToken);
        Assert.assertEquals(200, con.getResponseCode());
        var newContainerId2 = result(con);
        con = requestWithToken(PLATFORM_A, "DELETE", "/containers/" + newContainerId2, image, contContainerToken);
        Assert.assertEquals(200, con.getResponseCode());
        Thread.sleep(500);

        // Check if container can NOT perform actions which require "ADMIN" role
        con = requestWithToken(PLATFORM_A, "POST", "/users",
                user("forbiddenUser", "forbidden", Role.GUEST), contContainerToken);
        Assert.assertEquals(403, con.getResponseCode());
        Thread.sleep(500);

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

    @Test
    public void test12PutOwnContainer() throws Exception {
        var image = getSampleContainerImage();
        var token1 = getUserToken("contributor");
        var token2 = getUserToken("contributor2");

        // create container
        result(requestWithToken(PLATFORM_A, "POST", "/containers", image, token1));

        // owner can update the container...
        var con = requestWithToken(PLATFORM_A, "PUT", "/containers", image, token1);
        Assert.assertEquals(200, con.getResponseCode());
        var newContainerId = result(con);

        // ... but someone else can't
        con = requestWithToken(PLATFORM_A, "PUT", "/containers", image, token2);
        Assert.assertEquals(403, con.getResponseCode());

        // cleanup container
        result(requestWithToken(PLATFORM_A, "DELETE", "/containers/" + newContainerId, image, token1));
    }

    // Helper methods

    private Login createLogin(String username, String password) {
        return new Login(username, password);
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
