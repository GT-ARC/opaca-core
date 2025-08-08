package de.gtarc.opaca.platform.tests;


import de.gtarc.opaca.model.Login;
import de.gtarc.opaca.model.Role;
import de.gtarc.opaca.model.User;
import de.gtarc.opaca.platform.Application;
import static de.gtarc.opaca.platform.tests.TestUtils.*;

import org.junit.*;
import org.junit.rules.TestName;
import org.junit.runners.MethodSorters;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;

/**
 * Tests basic DB queries to the connected DB (default: embedded)
 * TODO update and fix docstring
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class UserTests {

    private static final int PLATFORM_A_PORT = 8006;
    private static final String PLATFORM_A = "http://localhost:" + PLATFORM_A_PORT;
    private static ConfigurableApplicationContext platformA = null;
    private static String adminToken = null;

    @BeforeClass
    public static void setupPlatform() throws Exception {
        platformA = SpringApplication.run(Application.class, "--server.port=" + PLATFORM_A_PORT,
                "--security.enableAuth=true",
                "--security.secret=top-secret-key-for-unit-testing",
                "--platform_admin_user=admin", "--platform_admin_pwd=12345",
                "--db_embed=true");

        adminToken = login("admin", "12345");
    }

    @AfterClass
    public static void stopPlatform() {
        platformA.close();
    }

    @Rule
    public TestName testName = new TestName();

    @Before
    public void printTest() {
        System.out.println(">>> RUNNING TEST UserTests." + testName.getMethodName());
    }

    @After
    public void checkInvariant() throws Exception {
        var con = requestWithToken(PLATFORM_A, "GET", "/users", null, adminToken);
        Assert.assertEquals(200, con.getResponseCode());
        var users = result(con, List.class);
        Assert.assertEquals(1, users.size()); // only admin
    }

    // POST /users

    @Test
    public void testCreateGetDelete() throws Exception {
        // create user
        var user = new User("name", "pwd", Role.USER, null);
        var con = requestWithToken(PLATFORM_A, "POST", "/users", user, adminToken);
        Assert.assertEquals(201, con.getResponseCode());

        // get user
        con = requestWithToken(PLATFORM_A, "GET", "/users/name", null, adminToken);
        Assert.assertEquals(200, con.getResponseCode());
        Assert.assertTrue(result(con).contains("username=name"));

        // delete user
        con = requestWithToken(PLATFORM_A, "DELETE", "/users/name", null, adminToken);
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void testCreateLogin() throws Exception {
        createUser(adminToken, "test1", "pwd", Role.USER, null);

        // new user can login and do stuff
        var con = request(PLATFORM_A, "POST", "/login", new Login("test1", "pwd"));
        Assert.assertEquals(200, con.getResponseCode());
        var userToken = result(con);
        con = requestWithToken(PLATFORM_A, "GET", "/info", null, userToken);
        Assert.assertEquals(200, con.getResponseCode());

        deleteUser(adminToken, "test1");
    }

    @Test
    public void testCreateUserAgain() throws Exception {
        createUser(adminToken, "test", "pwd", Role.USER, null);

        var user = new User("test", "otherPwd", Role.USER, null);
        var con = requestWithToken(PLATFORM_A, "POST", "/users", user, adminToken);
        Assert.assertEquals(409, con.getResponseCode());

        deleteUser(adminToken, "test");
    }

    @Test
    public void testCreateUserMissingRole() throws Exception {
        // create user with missing attributes
        var user = new User("differentUsername", "pwd", null, null);
        var con = requestWithToken(PLATFORM_A, "POST", "/users", user, adminToken);
        Assert.assertEquals(400, con.getResponseCode());
    }

    @Test
    public void testUserCreateUser() throws Exception {
        createUser(adminToken, "name", "pwd", Role.USER, null);
        var userToken = login("name", "pwd");

        // regular users can not create new users
        var user = new User("testUser2", "otherPwd", Role.USER, null);
        var con = requestWithToken(PLATFORM_A, "POST", "/users", user, userToken);
        Assert.assertEquals(403, con.getResponseCode());

        deleteUser(adminToken, "name");
    }

    // GET /users

    @Test
    public void testGetAllUsers() throws Exception {
        createUser(adminToken, "test1", "pwd", Role.USER, null);
        createUser(adminToken, "test2", "pwd", Role.USER, null);

        // admin can get all users
        var con = requestWithToken(PLATFORM_A, "GET", "/users", null, adminToken);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, List.class);
        Assert.assertEquals(3, res.size());

        deleteUser(adminToken, "test1");
        deleteUser(adminToken, "test2");
    }

    @Test
    public void testGetOneUser() throws Exception {
        createUser(adminToken, "test1", "pwd", Role.USER, null);
        var userToken = login("test1", "pwd");

        // user can get self
        var con = requestWithToken(PLATFORM_A, "GET", "/users/test1", null, userToken);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con); // result is user.toString (without pwd), not user itself
        Assert.assertTrue(res.contains("username=test1"));

        deleteUser(adminToken, "test1");
    }

    @Test
    public void testGetOtherUser() throws Exception {
        createUser(adminToken, "test1", "pwd", Role.USER, null);
        createUser(adminToken, "test2", "pwd", Role.USER, null);
        var userToken = login("test1", "pwd");

        // user can not get other user
        var con = requestWithToken(PLATFORM_A, "GET", "/users/test2", null, userToken);
        Assert.assertEquals(403, con.getResponseCode());

        deleteUser(adminToken, "test1");
        deleteUser(adminToken, "test2");
    }

    @Test
    public void testGetNonExistingUser() throws Exception {
        var con = requestWithToken(PLATFORM_A, "GET", "/users/missingUser", null, adminToken);
        Assert.assertEquals(404, con.getResponseCode());
    }

    // PUT /users

    @Test
    public void testEditCredentials() throws Exception {
        createUser(adminToken, "test1", "pwd", Role.USER, null);

        // admin edits user
        var edit = new User("test2", "newPwd", null, null);
        var con = requestWithToken(PLATFORM_A, "PUT", "/users/test1", edit, adminToken);
        Assert.assertEquals(200, con.getResponseCode());

        // try to login with old and new credentials
        con = request(PLATFORM_A, "POST", "/login", new Login("test1", "pwd"));
        Assert.assertEquals(403, con.getResponseCode());
        con = request(PLATFORM_A, "POST", "/login", new Login("test2", "newPwd"));
        Assert.assertEquals(200, con.getResponseCode());

        deleteUser(adminToken, "test2");
    }

    @Test
    public void testEditConflict() throws Exception {
        createUser(adminToken, "test1", "pwd", Role.USER, null);
        createUser(adminToken, "test2", "pwd", Role.USER, null);

        // try to edit user to get same username as other used
        var edit = new User("test2", null, null, null);
        var con = requestWithToken(PLATFORM_A, "PUT", "/users/test1", edit, adminToken);
        Assert.assertEquals(409, con.getResponseCode());

        deleteUser(adminToken, "test1");
        deleteUser(adminToken, "test2");
    }

    @Test
    public void testEditUnprivileged() throws Exception {
        createUser(adminToken, "test1", "pwd", Role.USER, null);
        var userToken = login("test1", "pwd");

        // user tries to edit itself (the actual edit does not matter, just to show why it's not allowed)
        var edit = new User(null, null, Role.ADMIN, null);
        var con = requestWithToken(PLATFORM_A, "PUT", "/users/test1", edit, userToken);
        Assert.assertEquals(403, con.getResponseCode());

        deleteUser(adminToken, "test1");
    }

    @Test
    public void testEditEverything() throws Exception {
        createUser(adminToken, "test1", "pwd", Role.USER, null);

        // alter all attributes
        var edit = new User("newName", "newPwd", Role.CONTRIBUTOR, List.of("Some_privilege"));
        var con = requestWithToken(PLATFORM_A, "PUT", "/users/test1", edit, adminToken);
        Assert.assertEquals(200, con.getResponseCode());
        // old user no longer exists
        con = requestWithToken(PLATFORM_A, "GET", "/users/test1", null, adminToken);
        Assert.assertEquals(404, con.getResponseCode());
        // new user exists and conforms to edit
        con = requestWithToken(PLATFORM_A, "GET", "/users/newName", null, adminToken);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con); // result is user.toString (without pwd), not user itself
        Assert.assertTrue(res.contains("newName"));
        Assert.assertTrue(res.contains("Some_privilege"));

        deleteUser(adminToken, "newName");
    }

    @Test
    public void testEditNothing() throws Exception {
        createUser(adminToken, "test1", "pwd", Role.USER, null);

        // get current user
        var con = requestWithToken(PLATFORM_A, "GET", "/users/test1", null, adminToken);
        var oldUser = result(con);

        // make pseudo edit-request without any changes
        var edit = new User(null, null, null, null);
        con = requestWithToken(PLATFORM_A, "PUT", "/users/test1", edit, adminToken);
        Assert.assertEquals(200, con.getResponseCode());
        Assert.assertEquals(oldUser, result(con));

        deleteUser(adminToken, "test1");
    }

    @Test
    public void testEditNonExisting() throws Exception {
        // try to edit non-existing user
        var edit = new User("newUsername", null, null, null);
        var con = requestWithToken(PLATFORM_A, "PUT", "/users/missingUser", edit, adminToken);
        Assert.assertEquals(404, con.getResponseCode());
    }

    // DELETE /users

    @Test
    public void testDeleteUser() throws Exception {
        createUser(adminToken, "test1", "pwd", Role.USER, null);

        // this is actually already tested in every single test, just for completeness...
        var con = requestWithToken(PLATFORM_A, "DELETE", "/users/test1", null, adminToken);
        Assert.assertEquals(200, con.getResponseCode());

        // user is no longer present
        con = requestWithToken(PLATFORM_A, "GET", "/users/test1", null, adminToken);
        Assert.assertEquals(404, con.getResponseCode());
    }

    @Test
    public void testDeleteUnprivileged() throws Exception {
        createUser(adminToken, "test1", "pwd", Role.USER, null);
        var userToken = login("test1", "pwd");

        // regular user can not delete, not even itself
        var con = requestWithToken(PLATFORM_A, "DELETE", "/users/admin", null, userToken);
        Assert.assertEquals(403, con.getResponseCode());

        deleteUser(adminToken, "test1");
    }

    @Test
    public void testDeleteThenLogin() throws Exception {
        createUser(adminToken, "test1", "pwd", Role.USER, null); // admin, so it can call other routes
        var userToken = login("test1", "pwd");
        deleteUser(adminToken, "test1");

        // after delete, user is no longer able to login...
        var con = request(PLATFORM_A, "POST", "/login", new Login("test1", "pwd"));
        Assert.assertEquals(403, con.getResponseCode());

        // ... or use a previously acquired token
        con = requestWithToken(PLATFORM_A, "GET", "/info", null, userToken);
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    public void testDeleteNonExisting() throws Exception {
        var con = requestWithToken(PLATFORM_A, "DELETE", "/users/missingUser", null, adminToken);
        Assert.assertEquals(404, con.getResponseCode());
    }

    /*
     * Short-hands for setup and cleanup of tests; just assume it works and return the result.
     * Generally, the setup and cleanup part of the above unit tests is done using these methods,
     * while the things under test are done by calling "request" directly (even if there is a
     * corresponding method for that call), in order to differentiate what the test is about.
     */

    private static String login(String user, String pwd) throws Exception {
        var con = request(PLATFORM_A, "POST", "/login", new Login(user, pwd));
        Assert.assertEquals(200, con.getResponseCode());
        return result(con);
    }

    private static void createUser(String token, String name, String pwd, Role role, List<String> privileges) throws Exception {
        var user = new User(name, pwd, role, privileges);
        var con = requestWithToken(PLATFORM_A, "POST", "/users", user, token);
        Assert.assertEquals(201, con.getResponseCode());
    }

    private static void deleteUser(String token, String name) throws Exception {
        var con = requestWithToken(PLATFORM_A, "DELETE", "/users/" + name, null, token);
        Assert.assertEquals(200, con.getResponseCode());
    }

}
