package de.gtarc.opaca.platform.tests;


import de.gtarc.opaca.model.Login;
import de.gtarc.opaca.model.Role;
import de.gtarc.opaca.platform.Application;
import static de.gtarc.opaca.platform.tests.TestUtils.*;

import org.junit.*;
import org.junit.runners.MethodSorters;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;

/**
 * Tests basic DB queries to the connected DB (default: embedded)
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class UserTests {

    private static final int PLATFORM_A_PORT = 8006;
    private final String PLATFORM_A = "http://localhost:" + PLATFORM_A_PORT;
    private static ConfigurableApplicationContext platformA = null;
    private static String token = null;

    @BeforeClass
    public static void setupPlatform() {
        platformA = SpringApplication.run(Application.class, "--server.port=" + PLATFORM_A_PORT,
                "--default_image_directory=./default-test-images", "--security.enableAuth=true",
                "--security.secret=top-secret-key-for-unit-testing",
                "--platform_admin_user=testUser", "--platform_admin_pwd=testPwd",
                "--db_type=embedded");
    }

    @AfterClass
    public static void stopPlatform() {
        platformA.close();
    }

    @Test
    public void test01GetToken() throws Exception {
        var con = request(PLATFORM_A, "POST", "/login", new Login("testUser", "testPwd"));
        Assert.assertEquals(200, con.getResponseCode());
        token = result(con);
        Assert.assertNotNull(token);
    }

    // POST /users

    @Test
    public void test02CreateUser() throws Exception {
        var con = requestWithToken(PLATFORM_A, "POST", "/users",
                getUser("username", "pwd", Role.ADMIN, null), token);
        Assert.assertEquals(201, con.getResponseCode());
    }

    @Test
    public void test02CreateUserAgain() throws Exception {
        var con = requestWithToken(PLATFORM_A, "POST", "/users",
                getUser("username", "otherPwd", Role.USER, null), token);
        Assert.assertEquals(400, con.getResponseCode());
    }

    @Test
    public void test02CreateUserMissingRole() throws Exception {
        var con = requestWithToken(PLATFORM_A, "POST", "/users",
                getUser("username", "pwd", null, null), token);
        Assert.assertEquals(400, con.getResponseCode());
    }

    // GET /users

    @Test
    public void test03GetAllUsers() throws Exception {
        var con = requestWithToken(PLATFORM_A, "GET", "/users", null, token);
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test03GetOneUser() throws Exception {
        var con = requestWithToken(PLATFORM_A, "GET", "/users/testUser", null, token);
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test03GetNonExistingUser() throws Exception {
        var con = requestWithToken(PLATFORM_A, "GET", "/users/missingUser", null, token);
        Assert.assertEquals(404, con.getResponseCode());
    }

    @Test
    public void test03GetWithPayload() throws Exception {
        var con = requestWithToken(PLATFORM_A, "GET", "/users/testUser",
                getUser("user", "password", Role.GUEST, null), token);
        Assert.assertEquals(405, con.getResponseCode());
    }

    // PUT /users

    @Test
    public void test04EditSuccess() throws Exception {
        var con = requestWithToken(PLATFORM_A, "PUT", "/users/testUser",
                getUser(null, "newPwd", null, null), token);
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test04EditEverything() throws Exception {
        var con = requestWithToken(PLATFORM_A, "PUT", "/users/username",
                getUser("newName", "newPwd", Role.USER, List.of("Some_privilege")), token);
        Assert.assertEquals(200, con.getResponseCode());
        // Check if login possible with new credentials
        con = request(PLATFORM_A, "POST", "/login", new Login("newName", "newPwd"));
        Assert.assertEquals(200, con.getResponseCode());
        // Check if login with old credentials is not possible anymore
        con = request(PLATFORM_A, "POST", "/login", new Login("username", "pwd"));
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    public void test04EditNothing() throws Exception {
        var con = requestWithToken(PLATFORM_A, "PUT", "/users/testUser",
                getUser(null, null, null, null), token);
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test04EditNonExisting() throws Exception {
        var con = requestWithToken(PLATFORM_A, "PUT", "/users/missingUser",
                getUser("newUsername", null, null, null), token);
        Assert.assertEquals(404, con.getResponseCode());
    }

    // DELETE /users

    @Test
    public void test05DeleteUser() throws Exception {
        var con = requestWithToken(PLATFORM_A, "DELETE", "/users/newName", null, token);
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test05DeleteUserDuplicate() throws Exception {
        var con = requestWithToken(PLATFORM_A, "DELETE", "/users/newName", null, token);
        Assert.assertEquals(404, con.getResponseCode());
    }

    @Test
    public void test05DeleteNonExisting() throws Exception {
        var con = requestWithToken(PLATFORM_A, "DELETE", "/users/missingUser", null, token);
        Assert.assertEquals(404, con.getResponseCode());
    }

}
