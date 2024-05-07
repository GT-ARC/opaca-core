package de.gtarc.opaca.platform.tests;

import de.gtarc.opaca.model.*;
import de.gtarc.opaca.platform.Application;
import static de.gtarc.opaca.platform.tests.TestUtils.*;

import de.gtarc.opaca.platform.session.Session;
import org.junit.*;
import org.junit.rules.TestName;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;

/**
 * Different tests testing especially stuff related to the Runtime Platform, such as deploying and removing
 * containers, connecting platforms, etc. The tests in this module should all be independent and able to
 * run individually or in any order.
 * At the start of this module, two Runtime Platforms are started, but not connected, without any containers.
 * The same state should also be maintained after each test.
 */
public class PlatformTests {

    private static final int PLATFORM_A_PORT = 8001;
    private static final int PLATFORM_B_PORT = 8002;

    private static final String PLATFORM_A_URL = "http://localhost:" + PLATFORM_A_PORT;
    private static final String PLATFORM_B_URL = "http://localhost:" + PLATFORM_B_PORT;

    private static ConfigurableApplicationContext platformA = null;
    private static ConfigurableApplicationContext platformB = null;

    @BeforeClass
    public static void setupPlatform() {
        platformA = SpringApplication.run(Application.class,
                "--server.port=" + PLATFORM_A_PORT,
                "--default_image_directory=./default-test-images"
        );
        platformB = SpringApplication.run(Application.class,
                "--server.port=" + PLATFORM_B_PORT);
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
        System.out.println(">>> RUNNING TEST PlatformTests." + testName.getMethodName());
    }

    @After
    public void checkInvariant() throws Exception {
        var con1 = request(PLATFORM_A_URL, "GET", "/info", null);
        var res1 = result(con1, RuntimePlatform.class);
        Assert.assertTrue(res1.getContainers().isEmpty());
        Assert.assertTrue(res1.getConnections().isEmpty());
        var con2 = request(PLATFORM_B_URL, "GET", "/info", null);
        var res2 = result(con2, RuntimePlatform.class);
        Assert.assertTrue(res2.getContainers().isEmpty());
        Assert.assertTrue(res2.getConnections().isEmpty());
    }

    /**
     * test that platform has correctly booted up and can return platform info
     */
    @Test
    public void testPlatformStarted() throws Exception {
        var con = request(PLATFORM_A_URL, "GET", "/info", null);
        Assert.assertEquals(200, con.getResponseCode());
        var info = result(con, RuntimePlatform.class);
        Assert.assertNotNull(info);
    }

    /**
     * check if default image is loaded on platform A, then undeploy it to not mess up the following tests
     */
    @Test
    public void testDefaultImage() throws Exception {
        var session = (Session) platformA.getBean("session");

        // create image file
        var imageFile = new File("./default-test-images/sample.json");
        if (!imageFile.getParentFile().exists()) imageFile.getParentFile().mkdirs();
        try (var writer = new FileWriter(imageFile)) {
            imageFile.createNewFile();
            writer.write("{ \"image\": { \"imageName\": \"" + TEST_IMAGE + "\" } }");
        }

        var defaultImages = session.readDefaultImages();
        Assert.assertEquals(defaultImages.size(), 1);
        Assert.assertEquals(defaultImages.get(0).getAbsolutePath(), imageFile.getAbsolutePath());

        imageFile.delete();
    }

    /**
     * deploy sample container
     */
    @Test
    public void testDeployAndUndeploy() throws Exception {
        // deploy
        var image = getSampleContainerImage();
        var con = request(PLATFORM_A_URL, "POST", "/containers", image);
        Assert.assertEquals(200, con.getResponseCode());
        var containerId = result(con);
        con = request(PLATFORM_A_URL, "GET", "/containers", null);
        var lst1 = result(con, List.class);
        Assert.assertEquals(1, lst1.size());

        // undeploy
        con = request(PLATFORM_A_URL, "DELETE", "/containers/" + containerId, null);
        Assert.assertEquals(200, con.getResponseCode());
        Assert.assertTrue(result(con, Boolean.class));
        con = request(PLATFORM_A_URL, "GET", "/containers", null);
        var lst2 = result(con, List.class);
        Assert.assertEquals(0, lst2.size());
    }

    /**
     * deploy a container with api port 8082 on platform B, which should now work since
     * the platform checks if a port is available before trying to start the container
     */
    @Test
    public void testPortMapping() throws Exception {
        var image = getSampleContainerImage();

        // post container to both platforms
        var con = request(PLATFORM_A_URL, "POST", "/containers", image);
        Assert.assertEquals(200, con.getResponseCode());
        var containerAId = result(con);
        Thread.sleep(1000);
        con = request(PLATFORM_B_URL, "POST", "/containers", image);
        Assert.assertEquals(200, con.getResponseCode());
        var containerBId = result(con);

        // check if container in platform B has higher port mapping than specified by image
        con = request(PLATFORM_B_URL, "GET", "/containers/" + containerBId, null);
        var container = result(con, AgentContainer.class);
        Assert.assertTrue(container.getConnectivity().getApiPortMapping() > image.getImage().getApiPort());

        // undeploy both container again
        con = request(PLATFORM_A_URL, "DELETE", "/containers/" + containerAId, null);
        Assert.assertEquals(200, con.getResponseCode());
        con = request(PLATFORM_B_URL, "DELETE", "/containers/" + containerBId, null);
        Assert.assertEquals(200, con.getResponseCode());
    }

    /**
     * deploy sample container, but with mismatched client config
     */
    @Test
    public void testDeployMismatchedConfig() throws Exception {
        var image = getSampleContainerImage();
        image.setClientConfig(new PostAgentContainer.KubernetesConfig());
        var con = request(PLATFORM_A_URL, "POST", "/containers", image);
        Assert.assertEquals(400, con.getResponseCode());
        var response = error(con);
        Assert.assertTrue(response.message.contains("does not match"));
    }

    /**
     * try to deploy unknown container
     *   -> 404 (not found)
     */
    @Test
    public void testDeployUnknown() throws Exception {
        var container = getSampleContainerImage();
        container.getImage().setImageName("does-not-exist-container-image");
        var con = request(PLATFORM_A_URL, "POST", "/containers", container);
        Assert.assertEquals(404, con.getResponseCode());
    }

    /**
     * try to deploy wrong type of container (just hello-world or similar)
     * deploy will work without error, but then all subsequent calls will fail
     *   -> 502 (bad gateway, after timeout)
     */
    @Test
    public void testDeployInvalid() throws Exception {
        var container = getSampleContainerImage();
        container.getImage().setImageName("hello-world");
        var con = request(PLATFORM_A_URL, "POST", "/containers", container);
        Assert.assertEquals(502, con.getResponseCode());

        con = request(PLATFORM_A_URL, "GET", "/containers", null);
        var lst = result(con, List.class);
        Assert.assertEquals(0, lst.size());
    }

    /**
     * try to undeploy unknown container
     * -> false (not really an error, afterward the container _is_ gone...)
     */
    @Test
    public void testUnknownUndeploy() throws Exception {
        var con = request(PLATFORM_A_URL, "DELETE", "/containers/somerandomcontainerid", null);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Boolean.class);
        Assert.assertFalse(res);
    }

    /**
     * try to GET an unknown route
     */
    @Test
    public void testUnknownRoute() throws Exception {
        var con = request(PLATFORM_A_URL, "GET", "/unknown", null);
        // this is actually a simple client-side error
        Assert.assertEquals(404, con.getResponseCode());
    }

    /**
     * try to POST to a route with invalid payload (for that route)
     */
    @Test
    public void testWrongPayload() throws Exception {
        var msg = Map.of("unknown", "attributes");
        var con = request(PLATFORM_A_URL, "POST", "/broadcast/topic", msg);
        Assert.assertEquals(422, con.getResponseCode());
    }

    /**
     * Test Event Logging by issuing some calls (successful and failing),
     * then see if the generated events match those calls.
     */
    @SuppressWarnings({"unchecked"})
    @Test
    public void testEventLogging() throws Exception {
        var message = Map.of("payload", "whatever", "replyTo", "");
        request(PLATFORM_A_URL, "POST", "/send/sample1", message);
        Thread.sleep(1000); // make sure calls finish in order
        request(PLATFORM_A_URL, "POST", "/invoke/UnknownAction", Map.of());
        Thread.sleep(1000); // wait for above calls to finish

        var con = request(PLATFORM_A_URL, "GET", "/history", null);
        List<Map<String, Object>> res = result(con, List.class);
        Assert.assertTrue(res.size() >= 4);
        Assert.assertEquals("CALL", res.get(res.size() - 4).get("eventType"));
        Assert.assertEquals(res.get(res.size() - 4).get("id"), res.get(res.size() - 3).get("relatedId"));
        Assert.assertEquals("POST /invoke/UnknownAction", res.get(res.size() - 2).get("route"));
        Assert.assertEquals("ERROR", res.get(res.size() - 1).get("eventType"));
    }

    /**
     * test that two containers get a different API port
     */
    @Test
    public void testFreePort() throws Exception {
        var image = getSampleContainerImage();

        // post 2 container to platform (A)
        var con = request(PLATFORM_A_URL, "POST", "/containers", image);
        Assert.assertEquals(200, con.getResponseCode());
        var container1Id = result(con);
        con = request(PLATFORM_A_URL, "POST", "/containers", image);
        Assert.assertEquals(200, con.getResponseCode());
        var container2Id = result(con);

        // check that the 2nd container has a higher api port than "normal", then delete containers
        try {
            con = request(PLATFORM_A_URL, "GET", "/containers/" + container2Id, null);
            var res = result(con, AgentContainer.class);
            Assert.assertTrue(res.getConnectivity().getApiPortMapping() > image.getImage().getApiPort());
        } finally {
            con = request(PLATFORM_A_URL, "DELETE", "/containers/" + container1Id, null);
            Assert.assertEquals(200, con.getResponseCode());
            con = request(PLATFORM_A_URL, "DELETE", "/containers/" + container2Id, null);
            Assert.assertEquals(200, con.getResponseCode());
        }
    }

    /**
     * connect to second platform, check that both are connected, then disconnect again
     */
    @Test
    public void testConnectAndDisconnect() throws Exception {
        var platformABaseUrl = getBaseUrl(PLATFORM_A_URL);
        var loginCon = new LoginConnection(null, null, platformABaseUrl);

        // connect platforms
        var con = request(PLATFORM_B_URL, "POST", "/connections", loginCon);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Boolean.class);
        Assert.assertTrue(res);

        // check that both platforms have registered each other in their connections
        con = request(PLATFORM_A_URL, "GET", "/connections", null);
        var lst1 = result(con, List.class);
        Assert.assertEquals(1, lst1.size());
        Assert.assertEquals(platformABaseUrl.replace(":8001", ":8002"), lst1.get(0));
        con = request(PLATFORM_B_URL, "GET", "/connections", null);
        var lst2 = result(con, List.class);
        Assert.assertEquals(1, lst2.size());
        Assert.assertEquals(platformABaseUrl, lst2.get(0));

        // disconnect platforms
        con = request(PLATFORM_B_URL, "DELETE", "/connections", platformABaseUrl);
        Assert.assertEquals(200, con.getResponseCode());
        res = result(con, Boolean.class);
        Assert.assertTrue(res);

        // check that both platforms are not registered as connected with each other anymore
        con = request(PLATFORM_A_URL, "GET", "/connections", null);
        lst1 = result(con, List.class);
        Assert.assertTrue(lst1.isEmpty());
        con = request(PLATFORM_B_URL, "GET", "/connections", null);
        lst2 = result(con, List.class);
        Assert.assertTrue(lst2.isEmpty());
    }

    @Test
    public void testConnectUnknown() throws Exception {
        var loginCon = new LoginConnection(null, null, "http://flsflsfsjfkj.com");
        var con = request(PLATFORM_A_URL, "POST", "/connections", loginCon);
        Assert.assertEquals(502, con.getResponseCode());
    }

    @Test
    public void testConnectInvalid() throws Exception {
        var loginCon = new LoginConnection(null, null, "not a valid url");
        var con = request(PLATFORM_A_URL, "POST", "/connections", loginCon);
        Assert.assertEquals(400, con.getResponseCode());
    }

    /**
     * TODO case is different if the platform _is_ connected, but does not respond to disconnect -> 502?
     */
    @Test
    public void testDisconnectUnknown() throws Exception {
        var con = request(PLATFORM_A_URL, "DELETE", "/connections", "http://flsflsfsjfkj.com");
        Assert.assertEquals(200, con.getResponseCode());

        // not really an error... afterward, the platform _is_ disconnected, it just never was connected, thus false
        var res = result(con, Boolean.class);
        Assert.assertFalse(res);
    }

    @Test
    public void testImageParams() throws Exception {
        var image = getSampleContainerImage();
        addImageParameters(image);
        image.setArguments(Map.of(
                "username", "theUsername",
                "password", "thePassword",
                "unknown", "whatever"
        ));

        // deploy container with parameters
        var con = request(PLATFORM_A_URL, "POST", "/containers", image);
        Assert.assertEquals(200, con.getResponseCode());
        var newContainerId = result(con);

        try {
            // check parameters in public /container info
            con = request(PLATFORM_A_URL, "GET", "/containers/" + newContainerId, null);
            var res1 = result(con, AgentContainer.class);
            Assert.assertEquals("mongodb", res1.getArguments().get("database"));
            Assert.assertEquals("theUsername", res1.getArguments().get("username"));
            Assert.assertFalse(res1.getArguments().containsKey("password"));
            Assert.assertFalse(res1.getArguments().containsKey("unknown"));

            // check parameters in container's own Environment
            var query = TestUtils.buildQuery(Map.of("containerId", newContainerId));
            con = request(PLATFORM_A_URL, "POST", "/invoke/GetEnv" + query, Map.of());
            Assert.assertEquals(200, con.getResponseCode());
            var res2 = result(con, Map.class);
            Assert.assertEquals("mongodb", res2.get("database"));
            Assert.assertEquals("theUsername", res2.get("username"));
            Assert.assertEquals("thePassword", res2.get("password"));
            Assert.assertFalse(res2.containsKey("unknown"));
        } finally {
            // stop container
            con = request(PLATFORM_A_URL, "DELETE", "/containers/" + newContainerId, null);
            Assert.assertEquals(200, con.getResponseCode());
        }
    }

}
