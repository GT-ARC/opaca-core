package de.dailab.jiacpp.platform.tests;

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

    private static final int PORT = 8001;

    private static final String PLATFORM_URL = "http://localhost:" + PORT;

    private static ConfigurableApplicationContext platform = null;

    @BeforeClass
    public static void setupPlatform() {
        platform = SpringApplication.run(Application.class,
                "--server.port=" + PORT,
                "--default_image_directory=./default-test-images"
        );
    }

    @AfterClass
    public static void stopPlatform() {
        platform.close();
    }

    /**
     * test that platform has correctly booted up and can return platform info
     */
    @Test
    public void testPlatformStarted() throws Exception {
        var con = request(PLATFORM_URL, "GET", "/info", null);
        Assert.assertEquals(200, con.getResponseCode());
        var info = result(con, RuntimePlatform.class);
        Assert.assertNotNull(info);
    }

    /**
     * check if default image is loaded on platform A, then undeploy it to not mess up the following tests
     */
    @Test
    public void testDefaultImage() throws Exception {
        var session = (Session) platform.getBean("session");

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
        var con = request(PLATFORM_URL, "POST", "/containers", image);
        Assert.assertEquals(200, con.getResponseCode());
        var containerId = result(con);
        con = request(PLATFORM_URL, "GET", "/containers", null);
        var lst1 = result(con, List.class);
        Assert.assertEquals(1, lst1.size());

        // undeploy
        con = request(PLATFORM_URL, "DELETE", "/containers/" + containerId, null);
        Assert.assertEquals(200, con.getResponseCode());
        Assert.assertTrue(result(con, Boolean.class));
        con = request(PLATFORM_URL, "GET", "/containers", null);
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
        var con = request(PLATFORM_URL, "POST", "/containers", image);
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
        var con = request(PLATFORM_URL, "POST", "/containers", container);
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
        var con = request(PLATFORM_URL, "POST", "/containers", container);
        Assert.assertEquals(502, con.getResponseCode());

        con = request(PLATFORM_URL, "GET", "/containers", null);
        var lst = result(con, List.class);
        Assert.assertEquals(0, lst.size());
    }

    /**
     * try to undeploy unknown container
     * -> false (not really an error, afterward the container _is_ gone...)
     */
    @Test
    public void testUnknownUndeploy() throws Exception {
        var con = request(PLATFORM_URL, "DELETE", "/containers/somerandomcontainerid", null);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Boolean.class);
        Assert.assertFalse(res);
    }

    /**
     * try to GET an unknown route
     */
    @Test
    public void testUnknownRoute() throws Exception {
        var con = request(PLATFORM_URL, "GET", "/unknown", null);
        // this is actually a simple client-side error
        Assert.assertEquals(404, con.getResponseCode());
    }

    /**
     * try to POST to a route with invalid payload (for that route)
     */
    @Test
    public void testWrongPayload() throws Exception {
        var msg = Map.of("unknown", "attributes");
        var con = request(PLATFORM_URL, "POST", "/broadcast/topic", msg);
        Assert.assertEquals(422, con.getResponseCode());
    }

}
