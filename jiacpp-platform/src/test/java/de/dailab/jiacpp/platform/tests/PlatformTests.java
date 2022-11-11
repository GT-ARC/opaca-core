package de.dailab.jiacpp.platform.tests;

import de.dailab.jiacpp.model.AgentContainer;
import de.dailab.jiacpp.model.AgentContainerImage;
import de.dailab.jiacpp.model.RuntimePlatform;
import de.dailab.jiacpp.util.RestHelper;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * The unit tests in this class test all the basic functionality of the Runtime Platform as well as some
 * error cases. The tests run against a live Runtime Platform that has to be started before the tests.
 *
 * - build everything with `mvn install`
 * - build the example container: `cd examples/sample-container; docker build -t sample-agent-container-image .`
 * - build the runtime platform docker image: `cd ../../jiacpp-platform; docker build -t jiacpp-platform .`
 * - start the runtime platform(s): `docker-compose up`
 *
 * NOTE: execution of the Runtime Platform in docker-compose does not work properly yet;
 * for now, just run it directly: `java -jar target/jiacpp-platform-0.1-SNAPSHOT.jar`
 *
 * Some tests depend on each others, so best always execute all tests. (That's also the reason
 * for the numbers in the method names, so don't remove those and stay consistent when adding more tests!)
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PlatformTests {

    private final String PLATFORM_A = "http://localhost:8001";
    private final String PLATFORM_B = "http://localhost:8002";

    private static String containerId = null;

    /*
     * TEST THAT STUFF WORKS
     */

    /**
     * call info, make sure platform is up
     */
    @Test
    public void test1Platform() throws Exception {
        var con = request(PLATFORM_A, "GET", "/info", null);
        Assert.assertEquals(200, con.getResponseCode());
        var info = result(con, RuntimePlatform.class);
        Assert.assertNotNull(info);
    }

    /**
     * deploy sample container
     */
    @Test
    public void test2Deploy() throws Exception {
        var container = new AgentContainerImage();
        container.setImageName("sample-agent-container-image");
        var con = request(PLATFORM_A, "POST", "/containers", container);
        Assert.assertEquals(200, con.getResponseCode());
        containerId = result(con);

        Thread.sleep(2000);

        con = request(PLATFORM_A, "GET", "/containers", null);
        var lst = result(con, List.class);
        Assert.assertEquals(1, lst.size());
    }

    /**
     * get container info
     */
    @Test
    public void test3GetInfo() throws Exception {
        var con = request(PLATFORM_A, "GET", "/containers/" + containerId, null);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, AgentContainer.class);
        Assert.assertEquals(containerId, res.getContainerId());
    }

    /**
     * call invoke, check result
     */
    @Test
    public void test4Invoke() throws Exception {
        var params = Map.of("x", 23, "y", 42);
        var con = request(PLATFORM_A, "POST", "/invoke/Add", params);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Integer.class);
        Assert.assertEquals(65L, res.longValue());
    }

    /**
     * call invoke with agent, check result
     */
    @Test
    public void test4InvokeNamed() throws Exception {
        for (String name : List.of("sample1", "sample2")) {
            var con = request(PLATFORM_A, "POST", "/invoke/GetInfo/" + name, Map.of());
            var res = result(con, Map.class);
            Assert.assertEquals(name, res.get("name"));
        }
    }


    /**
     * call send, check that it arrived via another invoke
     */
    @Test
    public void test5Send() throws Exception {
        var message = Map.of("payload", "testMessage", "replyTo", "doesnotmatter");
        var con = request(PLATFORM_A, "POST", "/send/sample1", message);
        Assert.assertEquals(200, con.getResponseCode());

        con = request(PLATFORM_A, "POST", "/invoke/GetInfo/sample1", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        Assert.assertEquals("testMessage", res.get("lastMessage"));
    }

    /**
     * call broadcast, check that it arrived via another invoke
     */
    @Test
    public void test5Broadcast() throws Exception {
        var message = Map.of("payload", "testBroadcast", "replyTo", "doesnotmatter");
        var con = request(PLATFORM_A, "POST", "/broadcast/topic", message);
        Assert.assertEquals(200, con.getResponseCode());

        con = request(PLATFORM_A, "POST", "/invoke/GetInfo/sample1", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        Assert.assertEquals("testBroadcast", res.get("lastBroadcast"));
    }

    // TODO connect to second platform, check that both are connected
    // TODO repeat above tests, but with redirect to second platform
    // TODO disconnect platforms, check that both are disconnected

    /**
     * undeploy container, check that it's gone
     */
    @Test
    public void test9Undeploy() throws Exception {
        var con = request(PLATFORM_A, "DELETE", "/containers/" + containerId, null);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Boolean.class);
        Assert.assertTrue(res);

        Thread.sleep(2000);

        con = request(PLATFORM_A, "GET", "/containers", null);
        var lst = result(con, List.class);
        Assert.assertEquals(0, lst.size());
    }

    /*
     * TEST HOW STUFF FAILS
     */

    /**
     * try to invoke unknown action
     * -> 404 (not found)
     */
    @Test
    public void test6unknownAction() throws Exception {
        var con = request(PLATFORM_A, "POST", "/invoke/UnknownAction", Map.of());
        Assert.assertEquals(404, con.getResponseCode());

        con = request(PLATFORM_A, "POST", "/invoke/Add/unknownagent", Map.of());
        Assert.assertEquals(404, con.getResponseCode());
    }

    /**
     * try to send message to unknown agent
     * -> 404 (not found)
     */
    @Test
    public void test6UnknownSend() throws Exception {
        var message = Map.of("payload", "testMessage", "replyTo", "doesnotmatter");
        var con = request(PLATFORM_A, "POST", "/send/unknownagent", message);
        Assert.assertEquals(404, con.getResponseCode());
    }

    // TODO try to call route with mismatched payload format?
    //  -> should be handled by Spring Boot, probably 412 or similar

    // TODO try to deploy unknown container
    //  -> 404 should make sense here

    // TODO try to deploy wrong type of container (just hello-world or similar)
    //  -> now _this_ is interesting... deploy will work without error, but then all subsequent calls will fail
    //     maybe deploy should wait until the container is online, and raise an exception after some time otherwise?

    /**
     * try to undeploy unknown container
     * -> false (not really an error, afterwards the container _is_ gone...)
     */
    @Test
    public void test6UnknownUndeploy() throws Exception {
        var con = request(PLATFORM_A, "DELETE", "/containers/somerandomcontainerid", null);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Boolean.class);
        Assert.assertFalse(res);
    }

    // TODO try to connect unknown platform
    //  -> what error? 404? or return false? or something like 502 bad gateway?

    // TODO try to disconnect unknown platform
    //  -> false (not really an error, afterwards the platform _is_ disconnected...)

    /*
     * HELPER METHODS
     */

    public HttpURLConnection request(String host, String method, String path, Object payload) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(host + path).openConnection();
        connection.setRequestMethod(method);

        if (payload != null) {
            String json = RestHelper.mapper.writeValueAsString(payload);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.connect();
            try (OutputStream os = connection.getOutputStream()) {
                os.write(bytes);
            }
        } else {
            connection.connect();
        }
        return connection;
    }

    public String result(HttpURLConnection connection) throws IOException {
        return new String(connection.getInputStream().readAllBytes());
    }

    public <T> T result(HttpURLConnection connection, Class<T> type) throws IOException {
        return RestHelper.mapper.readValue(connection.getInputStream(), type);
    }

}
