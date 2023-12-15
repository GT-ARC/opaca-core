package de.dailab.jiacpp.platform.tests;

import de.dailab.jiacpp.platform.Application;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static de.dailab.jiacpp.platform.tests.TestUtils.*;

public class InterPlatformTests {

    private static final int PLATFORM_A_PORT = 8004;
    private static final int PLATFORM_B_PORT = 8005;

    private static final String PLATFORM_A = "http://localhost:" + PLATFORM_A_PORT;
    private static final String PLATFORM_B = "http://localhost:" + PLATFORM_B_PORT;

    private static ConfigurableApplicationContext platformA = null;
    private static ConfigurableApplicationContext platformB = null;

    private static String containerId = null;

    @BeforeClass
    public static void setupPlatforms() throws IOException {
        platformA = SpringApplication.run(Application.class,
                "--server.port=" + PLATFORM_A_PORT);
        platformB = SpringApplication.run(Application.class,
                "--server.port=" + PLATFORM_B_PORT);
        containerId = postSampleContainer(PLATFORM_A);
        connectPlatforms(PLATFORM_B, PLATFORM_A);
    }

    @AfterClass
    public static void stopPlatforms() {
        platformA.close();
        platformB.close();
    }

    /**
     * call invoke, check result
     * (forwarded from platform B to platform A)
     */
    @Test
    public void testForwardInvokeAction() throws Exception {
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
    public void testForwardInvokeAgentAction() throws Exception {
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
    public void testForwardSend() throws Exception {
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
    public void testForwardBroadcast() throws Exception {
        var message = Map.of("payload", "testBroadcastForward", "replyTo", "doesnotmatter");
        var con = request(PLATFORM_B, "POST", "/broadcast/topic", message);
        Assert.assertEquals(200, con.getResponseCode());
        Thread.sleep(500);

        con = request(PLATFORM_A, "POST", "/invoke/GetInfo/sample1", Map.of());
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
        var con = request(PLATFORM_B, "POST", "/invoke/Add?forward=false", params);
        Assert.assertEquals(404, con.getResponseCode());
    }

    /**
     * send to agent at connected platform, but disallow forwarding
     */
    @Test
    public void testNoForwardSend() throws Exception {
        var message = Map.of("payload", "testMessage", "replyTo", "doesnotmatter");
        var con = request(PLATFORM_B, "POST", "/send/sample1?forward=false", message);
        Assert.assertEquals(404, con.getResponseCode());
    }

    /**
     * broadcast, but disallow forwarding
     */
    @Test
    public void testNoForwardBroadcast() throws Exception {
        var message = Map.of("payload", "testBroadcastNoForward", "replyTo", "doesnotmatter");
        var con = request(PLATFORM_B, "POST", "/broadcast/topic?forward=false", message);
        Assert.assertEquals(200, con.getResponseCode());
        // no error, but message was not forwarded
        con = request(PLATFORM_A, "POST", "/invoke/GetInfo/sample1", Map.of());
        var res = result(con, Map.class);
        Assert.assertNotEquals("testBroadcastNoForward", res.get("lastBroadcast"));
    }

    /**
     * TODO check somehow that notify worked
     */
    @Test
    public void testPlatformNotify() throws Exception {
        var platformABaseUrl = getBaseUrl(PLATFORM_A);
        var con2 = request(PLATFORM_B, "POST", "/connections/notify", platformABaseUrl);
        Assert.assertEquals(200, con2.getResponseCode());
    }

    @Test
    public void testInvalidPlatformNotify() throws Exception {
        // unknown platform
        var con2 = request(PLATFORM_A, "POST", "/connections/notify", "http://platform-does-not-exist.com");
        Assert.assertEquals(404, con2.getResponseCode());

        // invalid platform
        var con3 = request(PLATFORM_A, "POST", "/connections/notify", "invalid-url");
        Assert.assertEquals(400, con3.getResponseCode());
    }

    /**
     * TODO keep testing this? notify param in createAction might be obsolete.
     *  we could of course still keep it specifically for the purpose of this test.
     */
    @Test
    public void testAddNewActionManualNotify() throws Exception {
        // create new agent action
        var con = request(PLATFORM_A, "POST", "/invoke/CreateAction/sample1", Map.of("name", "TemporaryTestAction", "notify", "false"));
        Assert.assertEquals(200, con.getResponseCode());

        // new action has been created, but platform has not yet been notified --> action is unknown
        con = request(PLATFORM_A, "POST", "/invoke/TemporaryTestAction/sample1", Map.of());
        Assert.assertEquals(404, con.getResponseCode());

        // notify platform about updates in container, after which the new action is known
        con = request(PLATFORM_A, "POST", "/containers/notify", containerId);
        Assert.assertEquals(200, con.getResponseCode());

        // try to invoke the new action, which should now succeed
        con = request(PLATFORM_A, "POST", "/invoke/TemporaryTestAction/sample1", Map.of());
        Assert.assertEquals(200, con.getResponseCode());

        // platform A has also already notified platform B about its changes
        con = request(PLATFORM_B, "POST", "/invoke/TemporaryTestAction", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
    }

}
