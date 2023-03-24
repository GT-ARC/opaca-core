package de.dailab.jiacpp.platform.tests;

import com.fasterxml.jackson.databind.JsonNode;
import de.dailab.jiacpp.model.*;
import de.dailab.jiacpp.plattform.Application;
import de.dailab.jiacpp.util.RestHelper;

import org.junit.*;
import org.junit.runners.MethodSorters;

import org.springframework.boot.SpringApplication;

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
 * (for the connection-tests, repeat this for two platforms with ports 8001 and 8002 respectively)
 *
 * Some tests depend on each others, so best always execute all tests. (That's also the reason
 * for the numbers in the method names, so don't remove those and stay consistent when adding more tests!)
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PlatformTests {

    private static final String PLATFORM_A_PORT = "8001";
    private static final String PLATFORM_B_PORT = "8002";

    private final String PLATFORM_A = "http://localhost:" + PLATFORM_A_PORT;
    private final String PLATFORM_B = "http://localhost:" + PLATFORM_B_PORT;

    private final String TEST_IMAGE = "registry.gitlab.dai-labor.de/pub/unit-tests/jiacpp-sample-container";

    private static String containerId = null;
    private static String platformABaseUrl = null;

    /**
     * Before executing any of the tests, 2 test servers are started
     * on ports 8001 and 8002
     */
    @BeforeClass
    public static void setupPlatforms() {
        System.out.println("STARTING TEST SERVERS");

        System.out.println("STARTING TEST SERVER 1 ON PORT " + PLATFORM_A_PORT);
        SpringApplication.run(Application.class, "--server.port=" + PLATFORM_A_PORT);

        System.out.println("STARTING TEST SERVER 2 ON PORT " + PLATFORM_B_PORT);
        SpringApplication.run(Application.class, "--server.port=" + PLATFORM_B_PORT);
    }

    @AfterClass
    public static void cleanupPlatforms() {
        // todo: anything to do here? shut down the platforms?
    }

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
        platformABaseUrl = info.getBaseUrl();
    }

    /**
     * deploy sample container
     */
    @Test
    public void test2Deploy() throws Exception {
        var container = new AgentContainerImage();
        container.setImageName(TEST_IMAGE);
        var con = request(PLATFORM_A, "POST", "/containers", container);
        Assert.assertEquals(200, con.getResponseCode());
        containerId = result(con);

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

    @Test
    public void test3GetAgents() throws Exception {
        var con = request(PLATFORM_A, "GET", "/agents", null);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, List.class);
        Assert.assertEquals(2, res.size());
    }

    @Test
    public void test3GetAgent() throws Exception {
        var con = request(PLATFORM_A, "GET", "/agents/sample1", null);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, AgentDescription.class);
        Assert.assertEquals("sample1", res.getAgentId());
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

    /*
     * TODO test exposed ports
     *  - test that two containers get a different API port
     *  - test that container's /info route can be accessed via that port
     *  - test exposed extra port (has to be provided in sample image)
     *  - test that connectivity info is still there after /notify
     */

    /**
     * connect to second platform, check that both are connected
     */
    @Test
    public void test6Connect() throws Exception {
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

    // repeat above tests, but with redirect to second platform

    /**
     * call invoke, check result
     * (forwarded from platform B to platform A)
     */
    @Test
    public void test7ForwardInvoke() throws Exception {
        var params = Map.of("x", 23, "y", 42);
        var con = request(PLATFORM_B, "POST", "/invoke/Add", params);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Integer.class);
        Assert.assertEquals(65L, res.longValue());
    }

    /**
     * call invoke with agent, check result
     * (forwarded from platform B to platform A)
     */
    @Test
    public void test7ForwardInvokeNamed() throws Exception {
        for (String name : List.of("sample1", "sample2")) {
            var con = request(PLATFORM_B, "POST", "/invoke/GetInfo/" + name, Map.of());
            var res = result(con, Map.class);
            Assert.assertEquals(name, res.get("name"));
        }
    }

    /**
     * call send, check that it arrived via another invoke
     * (forwarded from platform B to platform A)
     */
    @Test
    public void test7ForwardSend() throws Exception {
        var message = Map.of("payload", "testMessage", "replyTo", "doesnotmatter");
        var con = request(PLATFORM_B, "POST", "/send/sample1", message);
        Assert.assertEquals(200, con.getResponseCode());

        con = request(PLATFORM_A, "POST", "/invoke/GetInfo/sample1", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        Assert.assertEquals("testMessage", res.get("lastMessage"));
    }

    /**
     * call broadcast, check that it arrived via another invoke
     * (forwarded from platform B to platform A)
     */
    @Test
    public void test7ForwardBroadcast() throws Exception {
        var message = Map.of("payload", "testBroadcast", "replyTo", "doesnotmatter");
        var con = request(PLATFORM_B, "POST", "/broadcast/topic", message);
        Assert.assertEquals(200, con.getResponseCode());

        con = request(PLATFORM_A, "POST", "/invoke/GetInfo/sample1", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        Assert.assertEquals("testBroadcast", res.get("lastBroadcast"));
    }

    // repeat tests again, with second platform, but disallowing forwarding

    /**
     * invoke method of connected platform, but disallow forwarding
     */
    @Test
    public void test7NoForwardInvoke() throws Exception {
        var params = Map.of("x", 23, "y", 42);
        var con = request(PLATFORM_B, "POST", "/invoke/Add?forward=false", params);
        Assert.assertEquals(404, con.getResponseCode());
    }

    /**
     * send to agent at connected platform, but disallow forwarding
     */
    @Test
    public void test7NoForwardSend() throws Exception {
        var message = Map.of("payload", "testMessage", "replyTo", "doesnotmatter");
        var con = request(PLATFORM_B, "POST", "/send/sample1?forward=false", message);
        Assert.assertEquals(404, con.getResponseCode());
    }

    /**
     * broadcast, but disallow forwarding
     */
    @Test
    public void test7NoForwardBroadcast() throws Exception {
        var message = Map.of("payload", "testBroadcastNoForward", "replyTo", "doesnotmatter");
        var con = request(PLATFORM_B, "POST", "/broadcast/topic?forward=false", message);
        Assert.assertEquals(200, con.getResponseCode());
        // no error, but message was not forwarded
        con = request(PLATFORM_A, "POST", "/invoke/GetInfo/sample1", Map.of());
        var res = result(con, Map.class);
        Assert.assertNotEquals("testBroadcastNoForward", res.get("lastBroadcast"));
    }

    /**
     * disconnect platforms, check that both are disconnected
     */
    @Test
    public void test8Disconnect() throws Exception {
        var con = request(PLATFORM_B, "DELETE", "/connections", platformABaseUrl);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Boolean.class);
        Assert.assertTrue(res);

        con = request(PLATFORM_A, "GET", "/connections", null);
        var lst1 = result(con, List.class);
        Assert.assertTrue(lst1.isEmpty());
        con = request(PLATFORM_B, "GET", "/connections", null);
        var lst2 = result(con, List.class);
        Assert.assertTrue(lst2.isEmpty());
    }

    /**
     * undeploy container, check that it's gone
     */
    @Test
    public void test9Undeploy() throws Exception {
        var con = request(PLATFORM_A, "DELETE", "/containers/" + containerId, null);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Boolean.class);
        Assert.assertTrue(res);

        con = request(PLATFORM_A, "GET", "/containers", null);
        var lst = result(con, List.class);
        Assert.assertEquals(0, lst.size());
    }

    /*
     * TEST HOW STUFF FAILS
     */

    @Test
    public void testXUnknownRoute() throws Exception {
        var con = request(PLATFORM_A, "GET", "/unknown", null);
        // this is actually a simple client-side error
        Assert.assertEquals(404, con.getResponseCode());
    }

    @Test
    public void testXWrongPayload() throws Exception {
        var msg = Map.of("unknown", "attributes");
        var con = request(PLATFORM_A, "GET", "/broadcast/topic", msg);
        Assert.assertEquals(422, con.getResponseCode());
    }

    @Test
    public void testXGetUnknownAgent() throws Exception {
        var con = request(PLATFORM_A, "GET", "/agents/unknown", null);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con);
        Assert.assertTrue(res.isEmpty());
    }

    /**
     * try to invoke unknown action
     * -> 404 (not found)
     */
    @Test
    public void testXUnknownAction() throws Exception {
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
    public void testXUnknownSend() throws Exception {
        var message = Map.of("payload", "testMessage", "replyTo", "doesnotmatter");
        var con = request(PLATFORM_A, "POST", "/send/unknownagent", message);
        Assert.assertEquals(404, con.getResponseCode());
    }

    // TODO invoke and send to known agent that does not respond on target container...
    //  needs actually faulty container; manually tested by stopping container outside of platform

    /**
     * try to deploy unknown container
     *   -> 404 (not found)
     */
    @Test
    public void testXDeployUnknown() throws Exception {
        var container = new AgentContainerImage();
        container.setImageName("does-not-exist-container-image");
        var con = request(PLATFORM_A, "POST", "/containers", container);
        Assert.assertEquals(404, con.getResponseCode());
    }

    /**
     * try to deploy wrong type of container (just hello-world or similar)
     * deploy will work without error, but then all subsequent calls will fail
     *   -> 502 (bad gateway, after timeout)
     */
    @Test
    public void testXDeployInvalid() throws Exception {
        var container = new AgentContainerImage();
        container.setImageName("hello-world");
        var con = request(PLATFORM_A, "POST", "/containers", container);
        Assert.assertEquals(502, con.getResponseCode());

        con = request(PLATFORM_A, "GET", "/containers", null);
        var lst = result(con, List.class);
        Assert.assertEquals(0, lst.size());
    }

    /**
     * try to undeploy unknown container
     * -> false (not really an error, afterwards the container _is_ gone...)
     */
    @Test
    public void testXUnknownUndeploy() throws Exception {
        var con = request(PLATFORM_A, "DELETE", "/containers/somerandomcontainerid", null);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Boolean.class);
        Assert.assertFalse(res);
    }

    @Test
    public void testXConnect() throws Exception {
        var con = request(PLATFORM_A, "POST", "/connections", "http://flsflsfsjfkj.com");
        Assert.assertEquals(502, con.getResponseCode());
    }

    @Test
    public void testXDisconnect() throws Exception {
        var con = request(PLATFORM_A, "DELETE", "/connections", "http://flsflsfsjfkj.com");
        Assert.assertEquals(200, con.getResponseCode());
        // not really an error... afterwards, the platform _is_ disconnected, it just never was connected, thus false
        // TODO case is different if the platform _is_ connected, but does not respond to disconnect -> 502?
        var res = result(con, Boolean.class);
        Assert.assertFalse(res);
    }

    @Test
    public void test7RequestNotify() throws Exception {
        // valid container
        var con1 = request(PLATFORM_A, "POST", "/containers/notify", containerId);
        Assert.assertEquals(200, con1.getResponseCode());

        // valid platform
        var con2 = request(PLATFORM_B, "POST", "/connections/notify", platformABaseUrl);
        Assert.assertEquals(200, con2.getResponseCode());
    }

    // TODO improve notify-test by actually having the sample container update its information (requires some extensions in the sample-container-agent code, though)

    @Test
    public void test7RequestInvalidNotify() throws Exception {
        // invalid container
        var con1 = request(PLATFORM_A, "POST", "/containers/notify", "container-does-not-exist");
        Assert.assertEquals(404, con1.getResponseCode());

        // invalid platform
        var con2 = request(PLATFORM_A, "POST", "/connections/notify", "platform-does-not-exist");
        Assert.assertEquals(404, con2.getResponseCode());
    }

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
