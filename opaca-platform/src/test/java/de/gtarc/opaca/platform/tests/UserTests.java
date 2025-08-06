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
                "--platform_admin_user=admin", "--platform_admin_pwd=12345",
                "--db_embed=true");
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

    @Test
    public void test01GetToken() throws Exception {
        var con = request(PLATFORM_A, "POST", "/login", new Login("admin", "12345"));
        Assert.assertEquals(200, con.getResponseCode());
        token = result(con);
        Assert.assertNotNull(token);
    }

    // POST /users

    @Test
    public void test02aCreateUser() throws Exception {
        var user = new User("testUser", "pwd", Role.ADMIN, null);
        var con = requestWithToken(PLATFORM_A, "POST", "/users", user, token);
        Assert.assertEquals(201, con.getResponseCode());
    }

    @Test
    public void test02bCreateUserAgain() throws Exception {
        var user = new User("testUser", "otherPwd", Role.USER, null);
        var con = requestWithToken(PLATFORM_A, "POST", "/users", user, token);
        Assert.assertEquals(400, con.getResponseCode());
    }

    @Test
    public void test02CreateUserMissingRole() throws Exception {
        var user = new User("differentUsername", "pwd", null, null);
        var con = requestWithToken(PLATFORM_A, "POST", "/users", user, token);
        // TODO fix this test was testing something else...
        // TODO check that user actually requires all those attributes
        Assert.assertEquals(400, con.getResponseCode());
    }

    // GET /users

    @Test
    public void test03GetAllUsers() throws Exception {
        var con = requestWithToken(PLATFORM_A, "GET", "/users", null, token);
        // TODO check users
        System.out.println(result(con));
        // TODO there should now be 2 users (admin and testuser)

        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test03GetOneUser() throws Exception {
        var con = requestWithToken(PLATFORM_A, "GET", "/users/testUser", null, token);
        // TODO check result
        // compare with expected
        System.out.println(result(con));
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test03GetNonExistingUser() throws Exception {
        var con = requestWithToken(PLATFORM_A, "GET", "/users/missingUser", null, token);
        Assert.assertEquals(404, con.getResponseCode());
    }

    // PUT /users

    @Test
    public void test04aEditSuccess() throws Exception {
        var edit = new User(null, "newPwd", null, null);
        var con = requestWithToken(PLATFORM_A, "PUT", "/users/testUser", edit, token);
        // TODO check result
        // TODO try to login with new password
        System.out.println(result(con));
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test04aEditConflict() throws Exception {
        var edit = new User("admin", "newPwd", null, null);
        var con = requestWithToken(PLATFORM_A, "PUT", "/users/testUser", edit, token);
        // TODO check result
        // TODO try to set username to existing, should fail
        Assert.assertEquals(400, con.getResponseCode());
    }

    @Test
    public void test04aEditMakeSudo() throws Exception {
        var edit = new User(null, "newPwd", Role.ADMIN, List.of("everything"));
        var con = requestWithToken(PLATFORM_A, "PUT", "/users/testUser", edit, token);
        // TODO check result
        // TODO use underpriviledged account to elevate own rights to admin, should fail
        Assert.assertEquals(400, con.getResponseCode());
    }


    @Test
    public void test04bEditEverything() throws Exception {
        var edit = new User("newName", "newPwd", Role.USER, List.of("Some_privilege"));
        var con = requestWithToken(PLATFORM_A, "PUT", "/users/testUser", edit, token);
        // TODO check result
        // TODO get user by new name, check what everything is there
        // TODO get user by old name, check that it's no longer there
        System.out.println(result(con));
        Assert.assertEquals(200, con.getResponseCode());
        // Check if login possible with new credentials
        con = request(PLATFORM_A, "POST", "/login", new Login("newName", "newPwd"));
        Assert.assertEquals(200, con.getResponseCode());
        // Check if login with old credentials is not possible anymore
        con = request(PLATFORM_A, "POST", "/login", new Login("username", "pwd"));
        Assert.assertEquals(403, con.getResponseCode());
    }

    @Test
    public void test04cEditNothing() throws Exception {
        var edit = new User(null, null, null, null);
        var con = requestWithToken(PLATFORM_A, "PUT", "/users/newName", edit, token);
        // TODO check result
        // TODO check what the result is unchanged
        System.out.println(result(con));
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test04cEditNonExisting() throws Exception {
        var edit = new User("newUsername", null, null, null);
        var con = requestWithToken(PLATFORM_A, "PUT", "/users/missingUser", edit, token);
        Assert.assertEquals(404, con.getResponseCode());
    }

    // DELETE /users

    @Test
    public void test05DeleteUser() throws Exception {
        var con = requestWithToken(PLATFORM_A, "DELETE", "/users/newName", null, token);
        // TODO check result
        // TODO check what the user is no longer there
        System.out.println(result(con));
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test05DeleteNonExisting() throws Exception {
        var con = requestWithToken(PLATFORM_A, "DELETE", "/users/missingUser", null, token);
        Assert.assertEquals(404, con.getResponseCode());
    }

}
