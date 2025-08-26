package de.gtarc.opaca.platform.tests;

import de.gtarc.opaca.model.RuntimePlatform;
import de.gtarc.opaca.platform.Application;
import org.junit.*;
import org.junit.rules.TestName;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;
import java.util.Map;

import static de.gtarc.opaca.platform.tests.TestUtils.*;

/**
 * The tests in this module test the communication between platform. During setup, two runtime platforms are started,
 * the platforms are connected, and a sample-agent-container is deployed on the first platform. This setup should not
 * be changed by tests (or if changed, then those changes should be reverted in the test itself) so tests can all
 * run independently or in any order.
 */
public class InterPlatformTests {

    private static final int PLATFORM_A_PORT = 8004;
    private static final int PLATFORM_B_PORT = 8005;

    private static final String PLATFORM_A_URL = "http://localhost:" + PLATFORM_A_PORT;
    private static final String PLATFORM_B_URL = "http://localhost:" + PLATFORM_B_PORT;

    private static ConfigurableApplicationContext platformA = null;
    private static ConfigurableApplicationContext platformB = null;

    private static String containerId = null;

    @BeforeClass
    public static void setupPlatforms() throws Exception {
        platformA = SpringApplication.run(Application.class,
                "--server.port=" + PLATFORM_A_PORT);
        platformB = SpringApplication.run(Application.class,
                "--server.port=" + PLATFORM_B_PORT);
        containerId = postSampleContainer(PLATFORM_A_URL);
        connectPlatforms(PLATFORM_B_URL, PLATFORM_A_URL);
        checkInvariantStatic();
    }

    @AfterClass
    public static void stopPlatforms() {
        platformA.close();
        platformB.close();
    }

    @Rule
    public TestName testName = new TestName();

    @Before
    public void printTest() {
        System.out.println(">>> RUNNING TEST InterPlatformTests." + testName.getMethodName());
    }

    @After
    public void checkInvariant() throws Exception {
        checkInvariantStatic();
    }

    public static void checkInvariantStatic() throws Exception {
        var con1 = request(PLATFORM_A_URL, "GET", "/info", null);
        var res1 = result(con1, RuntimePlatform.class);
        Assert.assertEquals(1, res1.getConnections().size());
        Assert.assertEquals(1, res1.getConnections().size());
        var agents = res1.getContainers().get(0).getAgents();
        Assert.assertEquals(2, agents.size());
        var con2 = request(PLATFORM_B_URL, "GET", "/info", null);
        var res2 = result(con2, RuntimePlatform.class);
        Assert.assertTrue(res2.getContainers().isEmpty());
        Assert.assertEquals(1, res2.getConnections().size());
    }

    /**
     * call get-agents with or without "includeConnected" parameter
     */
    @Test
    public void testGetAgents() throws Exception {
        var con = request(PLATFORM_A_URL, "GET", "/agents", null);
        var res = result(con, List.class);
        Assert.assertEquals(2, res.size());
        con = request(PLATFORM_B_URL, "GET", "/agents", null);
        res = result(con, List.class);
        Assert.assertEquals(0, res.size());
        con = request(PLATFORM_B_URL, "GET", "/agents?includeConnected=true", null);
        res = result(con, List.class);
        Assert.assertEquals(2, res.size());
    }

    /**
     * call invoke, check result
     * (forwarded from platform B to platform A)
     */
    @Test
    public void testForwardInvokeAction() throws Exception {
        var params = Map.of("x", 23, "y", 42);
        var con = request(PLATFORM_B_URL, "POST", "/invoke/Add", params);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Integer.class);
        Assert.assertEquals(65L, res.longValue());
    }

    /**
     * call invoke with agent, check result
     * (forwarded from platform B to platform A)
     */
    @Test
    public void testForwardInvokeAgentAction() throws Exception {
        for (String name : List.of("sample1", "sample2")) {
            var con = request(PLATFORM_B_URL, "POST", "/invoke/GetInfo/" + name, Map.of());
            Assert.assertEquals(200, con.getResponseCode());
            var res = result(con, Map.class);
            Assert.assertEquals(name, res.get("name"));
        }
    }

    /**
     * call send, check that it arrived via another invoke
     * (forwarded from platform B to platform A)
     */
    @Test
    public void testForwardSend() throws Exception {
        var message = Map.of("payload", "testMessage", "replyTo", "doesnotmatter");
        var con = request(PLATFORM_B_URL, "POST", "/send/sample1", message);
        Assert.assertEquals(200, con.getResponseCode());

        con = request(PLATFORM_A_URL, "POST", "/invoke/GetInfo/sample1", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        Assert.assertEquals("testMessage", res.get("lastMessage"));
    }

    /**
     * call broadcast, check that it arrived via another invoke
     * (forwarded from platform B to platform A)
     */
    @Test
    public void testForwardBroadcast() throws Exception {
        var message = Map.of("payload", "testBroadcastForward", "replyTo", "doesnotmatter");
        var con = request(PLATFORM_B_URL, "POST", "/broadcast/topic", message);
        Assert.assertEquals(200, con.getResponseCode());
        Thread.sleep(500);

        con = request(PLATFORM_A_URL, "POST", "/invoke/GetInfo/sample1", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        Assert.assertEquals("testBroadcastForward", res.get("lastBroadcast"));
    }

    /**
     * invoke method of connected platform, but disallow forwarding
     */
    @Test
    public void testNoForwardInvoke() throws Exception {
        var params = Map.of("x", 23, "y", 42);
        var con = request(PLATFORM_B_URL, "POST", "/invoke/Add?forward=false", params);
        Assert.assertEquals(404, con.getResponseCode());
    }

    /**
     * send to agent at connected platform, but disallow forwarding
     */
    @Test
    public void testNoForwardSend() throws Exception {
        var message = Map.of("payload", "testMessage", "replyTo", "doesnotmatter");
        var con = request(PLATFORM_B_URL, "POST", "/send/sample1?forward=false", message);
        Assert.assertEquals(404, con.getResponseCode());
    }

    /**
     * broadcast, but disallow forwarding
     */
    @Test
    public void testNoForwardBroadcast() throws Exception {
        var message = Map.of("payload", "testBroadcastNoForward", "replyTo", "doesnotmatter");
        var con = request(PLATFORM_B_URL, "POST", "/broadcast/topic?forward=false", message);
        Assert.assertEquals(200, con.getResponseCode());
        Thread.sleep(500);

        // no error, but message was not forwarded
        con = request(PLATFORM_A_URL, "POST", "/invoke/GetInfo/sample1", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        Assert.assertNotEquals("testBroadcastNoForward", res.get("lastBroadcast"));
    }

    /**
     * call action to create a new action, then check that auto-notify worked and propagated the new action
     * to the connected platform, so it can be called from there.
     */
    @Test
    public void testPlatformNotify() throws Exception {

        // add temp container to platform B, then create new action in that container
        var newContainerId = postSampleContainer(PLATFORM_B_URL);
        var con = request(PLATFORM_B_URL, "POST", "/invoke/CreateAction?containerId=" + newContainerId,
                Map.of("name", "TemporaryTestAction", "notify", true));
        Assert.assertEquals(200, con.getResponseCode());

        // try to call the new temporary test action on platform A, which should have been notified automatically at this point
        con = request(PLATFORM_A_URL, "POST", "/invoke/TemporaryTestAction", Map.of());
        Assert.assertEquals(200, con.getResponseCode());

        // undeploy temp container
        con = request(PLATFORM_B_URL, "DELETE", "/containers/" + newContainerId, null);
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void testInvalidPlatformNotify() throws Exception {
        // unknown platform
        var con2 = request(PLATFORM_A_URL, "POST", "/connections/notify", "http://platform-does-not-exist.com");
        Assert.assertEquals(404, con2.getResponseCode());

        // invalid platform
        var con3 = request(PLATFORM_A_URL, "POST", "/connections/notify", "invalid-url");
        Assert.assertEquals(400, con3.getResponseCode());
    }

    /**
     * analogous to {@link #testPlatformNotify()}, but suppressing auto-notify and calling notify manually
     */
    @Test
    public void testAddNewActionManualNotify() throws Exception {
        // create new agent action
        var con = request(PLATFORM_A_URL, "POST", "/invoke/CreateAction/sample1", Map.of("name", "TemporaryTestAction", "notify", false));
        Assert.assertEquals(200, con.getResponseCode());

        // new action has been created, but platform has not yet been notified --> action is unknown
        con = request(PLATFORM_A_URL, "POST", "/invoke/TemporaryTestAction/sample1", Map.of());
        Assert.assertEquals(404, con.getResponseCode());

        // notify platform about updates in container, after which the new action is known
        con = request(PLATFORM_A_URL, "POST", "/containers/notify", containerId);
        Assert.assertEquals(200, con.getResponseCode());

        // try to invoke the new action, which should now succeed
        con = request(PLATFORM_A_URL, "POST", "/invoke/TemporaryTestAction/sample1", Map.of());
        Assert.assertEquals(200, con.getResponseCode());

        // platform A has also already notified platform B about its changes
        con = request(PLATFORM_B_URL, "POST", "/invoke/TemporaryTestAction", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
    }

}
