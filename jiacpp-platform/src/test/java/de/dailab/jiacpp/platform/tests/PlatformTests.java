package de.dailab.jiacpp.platform.tests;

import de.dailab.jiacpp.model.AgentContainer;
import de.dailab.jiacpp.model.PostAgentContainer;
import de.dailab.jiacpp.model.RuntimePlatform;
import de.dailab.jiacpp.platform.Application;
import de.dailab.jiacpp.platform.session.Session;
import org.junit.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;

import static de.dailab.jiacpp.platform.tests.TestUtils.*;

public class PlatformTests {

    private static final int PORT_A = 8001;

    private static final int PORT_B = 8002;

    private static final String PLATFORM_A = "http://localhost:" + PORT_A;

    private static final String PLATFORM_B = "http://localhost:" + PORT_B;

    private static ConfigurableApplicationContext platformA = null;

    private static ConfigurableApplicationContext platformB = null;

    @BeforeClass
    public static void setupPlatform() {
        platformA = SpringApplication.run(Application.class,
                "--server.port=" + PORT_A,
                "--default_image_directory=./default-test-images"
        );
        platformB = SpringApplication.run(Application.class,
                "--server.port=" + PORT_A);
    }

    @AfterClass
    public static void stopPlatform() {
        platformA.close();
        platformB.close();
    }

    /**
     * test that platform has correctly booted up and can return platform info
     */
    @Test
    public void testPlatformStarted() throws Exception {
        var con = request(PLATFORM_A, "GET", "/info", null);
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
        var con = request(PLATFORM_A, "POST", "/containers", image);
        Assert.assertEquals(200, con.getResponseCode());
        var containerId = result(con);
        con = request(PLATFORM_A, "GET", "/containers", null);
        var lst1 = result(con, List.class);
        Assert.assertEquals(1, lst1.size());

        // undeploy
        con = request(PLATFORM_A, "DELETE", "/containers/" + containerId, null);
        Assert.assertEquals(200, con.getResponseCode());
        Assert.assertTrue(result(con, Boolean.class));
        con = request(PLATFORM_A, "GET", "/containers", null);
        var lst2 = result(con, List.class);
        Assert.assertEquals(0, lst2.size());
    }

    /**
     * deploy sample container, but with mismatched client config
     */
    @Test
    public void testDeployMismatchedConfig() throws Exception {
        var image = getSampleContainerImage();
        image.setClientConfig(new PostAgentContainer.KubernetesConfig());
        var con = request(PLATFORM_A, "POST", "/containers", image);
        Assert.assertEquals(400, con.getResponseCode());
        Assert.assertTrue(error(con).contains("does not match"));
    }

    /**
     * try to deploy unknown container
     *   -> 404 (not found)
     */
    @Test
    public void testDeployUnknown() throws Exception {
        var container = getSampleContainerImage();
        container.getImage().setImageName("does-not-exist-container-image");
        var con = request(PLATFORM_A, "POST", "/containers", container);
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
        var con = request(PLATFORM_A, "POST", "/containers", container);
        Assert.assertEquals(502, con.getResponseCode());

        con = request(PLATFORM_A, "GET", "/containers", null);
        var lst = result(con, List.class);
        Assert.assertEquals(0, lst.size());
    }

    /**
     * try to undeploy unknown container
     * -> false (not really an error, afterward the container _is_ gone...)
     */
    @Test
    public void testUnknownUndeploy() throws Exception {
        var con = request(PLATFORM_A, "DELETE", "/containers/somerandomcontainerid", null);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Boolean.class);
        Assert.assertFalse(res);
    }

    /**
     * try to GET an unknown route
     */
    @Test
    public void testUnknownRoute() throws Exception {
        var con = request(PLATFORM_A, "GET", "/unknown", null);
        // this is actually a simple client-side error
        Assert.assertEquals(404, con.getResponseCode());
    }

    /**
     * try to POST to a route with invalid payload (for that route)
     */
    @Test
    public void testWrongPayload() throws Exception {
        var msg = Map.of("unknown", "attributes");
        var con = request(PLATFORM_A, "POST", "/broadcast/topic", msg);
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
        request(PLATFORM_A, "POST", "/send/sample1", message);
        Thread.sleep(1000); // make sure calls finish in order
        request(PLATFORM_A, "POST", "/invoke/UnknownAction", Map.of());
        Thread.sleep(1000); // wait for above calls to finish

        var con = request(PLATFORM_A, "GET", "/history", null);
        List<Map<String, Object>> res = result(con, List.class);
        Assert.assertTrue(res.size() >= 4);
        Assert.assertEquals("API_CALL", res.get(res.size() - 4).get("eventType"));
        Assert.assertEquals(res.get(res.size() - 4).get("id"), res.get(res.size() - 3).get("relatedId"));
        Assert.assertEquals("invoke", res.get(res.size() - 2).get("methodName"));
        Assert.assertEquals("API_ERROR", res.get(res.size() - 1).get("eventType"));
    }

    /**
     * test that two containers get a different API port
     */
    @Test
    public void testFreePort() throws Exception {
        var image = getSampleContainerImage();
        var con = request(PLATFORM_A, "POST", "/containers", image);
        Assert.assertEquals(200, con.getResponseCode());
        var newContainerId = result(con);

        try {
            con = request(PLATFORM_A, "GET", "/containers/" + newContainerId, null);
            var res = result(con, AgentContainer.class);
            Assert.assertEquals(8083, (int) res.getConnectivity().getApiPortMapping());
        } finally {
            con = request(PLATFORM_A, "DELETE", "/containers/" + newContainerId, null);
            Assert.assertEquals(200, con.getResponseCode());
        }
    }

    /**
     * connect to second platform, check that both are connected
     */
    @Test
    public void testConnect() throws Exception {
        var platformABaseUrl = getBaseUrl(PLATFORM_A);
        var con = request(PLATFORM_B, "POST", "/connections", platformABaseUrl);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Boolean.class);
        Assert.assertTrue(res);

        con = request(PLATFORM_A, "GET", "/connections", null);
        var lst1 = result(con, List.class);
        Assert.assertEquals(1, lst1.size());
        Assert.assertEquals(platformABaseUrl.replace(":8001", ":8002"), lst1.get(0));
        con = request(PLATFORM_B, "GET", "/connections", null);
        var lst2 = result(con, List.class);
        Assert.assertEquals(1, lst2.size());
        Assert.assertEquals(platformABaseUrl, lst2.get(0));
    }

}
