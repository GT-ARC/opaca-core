package de.dailab.jiacpp.platform.tests;

import de.dailab.jiacpp.api.AgentContainerApi;
import de.dailab.jiacpp.model.*;
import de.dailab.jiacpp.platform.Application;
import de.dailab.jiacpp.platform.PlatformRestController;
import static de.dailab.jiacpp.platform.tests.TestUtils.*;

import de.dailab.jiacpp.platform.session.Session;
import org.junit.*;
import org.junit.runners.MethodSorters;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The unit tests in this class test all the basic functionality of the Runtime Platform as well as some
 * error cases. The tests run against two live Runtime Platforms that are started along with the tests.
 * The sample container image is taken from the DAI Gitlab Container Registry.
 *
 * Some tests depend on each others, so best always execute all tests. (That's also the reason
 * for the numbers in the method names, so don't remove those and stay consistent when adding more tests!)
 *
 * During initial setup and the first tests, two Runtime Platforms are started, a sample-container is
 * deployed on the first one, and the two platforms are connected.
 *
 *   Platform A   -----------------   Platform B
 *       |
 *  Sample-Container-Image
 *
 * Thus, this is the setup that can be assumed for most tests. Individual tests may deploy additional
 * containers needed for testing, but then they should undeploy them when they are done.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PlatformTests {

    private static final String PLATFORM_A_PORT = "8001";
    private static final String PLATFORM_B_PORT = "8002";

    private final String PLATFORM_A = "http://localhost:" + PLATFORM_A_PORT;
    private final String PLATFORM_B = "http://localhost:" + PLATFORM_B_PORT;

    private static ConfigurableApplicationContext platformA = null;
    private static ConfigurableApplicationContext platformB = null;
    private static String containerId = null;
    private static String platformABaseUrl = null;


    /**
     * Before executing any of the tests, 2 test servers are started
     * on ports 8001 and 8002
     */
    @BeforeClass
    public static void setupPlatforms() {
        platformA = SpringApplication.run(Application.class, "--server.port=" + PLATFORM_A_PORT,
                "--default_image_directory=./default-test-images");
        platformB = SpringApplication.run(Application.class, "--server.port=" + PLATFORM_B_PORT);
    }

    @AfterClass
    public static void stopPlatforms() {
        platformA.close();
        platformB.close();
    }

    /*
     * TEST THAT STUFF WORKS
     */

    /**
     * check if default image is loaded on platform A, then undeploy it to not mess up the following tests
     */
    @Test
    public void test1DefaultImage() throws Exception {
        var session = (Session) platformA.getBean("session");

        // create image file
        var imageFile = new File("./default-test-images/sample.json");
        if (!imageFile.getParentFile().exists()) imageFile.getParentFile().mkdirs();
        try (var writer = new FileWriter(imageFile)) {
            imageFile.createNewFile();
            writer.write("{ \"imageName\": \"" + TEST_IMAGE + "\" }");
        }

        var defaultImages = session.readDefaultImages();
        Assert.assertEquals(defaultImages.size(), 1);
        Assert.assertEquals(defaultImages.get(0).getAbsolutePath(), imageFile.getAbsolutePath());

        imageFile.delete();
    }

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
        var image = getSampleContainerImage();
        var con = request(PLATFORM_A, "POST", "/containers", image);
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
     * invoke long-running action at two agents in parallel; the agents may be "busy" until the first action is through
     * (and indeed they are in the JIAC VI reference impl), but the ContainerAgent (and of course the Swagger UI) are
     * still responsive and can take on tasks for other agents.
     */
    @Test
    public void test4InvokeNonblocking() throws Exception {
        // TODO inner Asserts do not seem to make the test fail!
        long start = System.currentTimeMillis();
        List<Thread> threads = Stream.of("sample1", "sample2")
                .map(agent -> new Thread(() -> {
                    try {
                        var con = request(PLATFORM_A, "POST", "/invoke/DoThis/" + agent,
                                Map.of("message", "", "sleep_seconds", 5));
                        Assert.assertEquals(200, con.getResponseCode());
                    } catch (Exception e) {
                        Assert.fail(e.getMessage());
                    }
                })).collect(Collectors.toList());
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();
        System.out.println(System.currentTimeMillis() - start);
        Assert.assertTrue(System.currentTimeMillis() - start < 8 * 1000);
    }

    @Test
    public void test4InvokeFail() throws Exception {
        var con = request(PLATFORM_A, "POST", "/invoke/Fail", Map.of());
        Assert.assertEquals(502, con.getResponseCode());
        var msg = error(con);
        Assert.assertTrue(msg.contains("Action Failed (as expected)"));
    }

    @Test
    public void test4InvokeTimeout() throws Exception {
        var params = Map.of("message", "timeout-test", "sleep_seconds", 5);
        var con = request(PLATFORM_A, "POST", "/invoke/DoThis?timeout=2", params);
        Assert.assertEquals(502, con.getResponseCode());
        // TODO this is not ideal yet... the original error may contain a descriptive message that is lost
    }

    /**
     * invoke action with mismatched/missing parameters
     */
    @Test
    public void test4InvokeParamMismatch() throws Exception {
        var con = request(PLATFORM_A, "POST", "/invoke/DoThis/",
                Map.of("message", "missing 'sleep_seconds' parameter!"));
        Assert.assertEquals(502, con.getResponseCode());
        // TODO case of missing parameter could also be handled by platform, resulting in 422 error
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

    /**
     * if auth is disabled, no token should be passed to container
     */
    @Test
    public void test5AuAuthNoToken() throws Exception {
        var con = request(PLATFORM_A, "POST", "/invoke/GetInfo", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        Assert.assertEquals("", res.get("TOKEN"));
    }

    /**
     * Test Event Logging by issuing some calls (successful and failing),
     * then see if the generated events match those calls.
     */
    @SuppressWarnings({"unchecked"})
    @Test
    public void test5EventLogging() throws Exception {
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
    public void test5FreePort() throws Exception {
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
     * test that container's /info route can be accessed via that port
     */
    @Test
    public void test5ApiPort() throws Exception {
        var con = request(PLATFORM_A, "GET", "/containers/" + containerId, null);
        var res = result(con, AgentContainer.class).getConnectivity();

        // access /info route through exposed port
        var url = String.format("%s:%s", res.getPublicUrl(), res.getApiPortMapping());
        System.out.println(url);
        con = request(url, "GET", "/info", null);
        Assert.assertEquals(200, con.getResponseCode());
    }

    /**
     * test exposed extra port (has to be provided in sample image)
     */
    @Test
    public void test5ExtraPortTCP() throws Exception {
        var con = request(PLATFORM_A, "GET", "/containers/" + containerId, null);
        var res = result(con, AgentContainer.class).getConnectivity();

        var url = String.format("%s:%s", res.getPublicUrl(), "8888");
        con = request(url, "GET", "/", null);
        Assert.assertEquals(200, con.getResponseCode());
        Assert.assertEquals("It Works!", result(con));
    }

    @Test
    public void test5ExtraPortUDP() throws Exception {
        var addr = InetAddress.getByName("localhost");
        DatagramSocket clientSocket = new DatagramSocket();
        byte[] sendData = "Test".getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, addr, 8889);
        clientSocket.send(sendPacket);

        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        clientSocket.receive(receivePacket);

        String receivedMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());
        Assert.assertEquals("TestTest", receivedMessage);

        clientSocket.close();
    }

    /**
     * test that connectivity info is still there after /notify
     */
    @Test
    public void test5NotifyConnectivity() throws Exception {
        var con = request(PLATFORM_A, "POST", "/containers/notify", containerId);

        con = request(PLATFORM_A, "GET", "/containers/" + containerId, null);
        var res = result(con, AgentContainer.class);
        Assert.assertNotNull(res.getConnectivity());
    }

    /**
     * Deploy an additional sample-container on the same platform, so there are two of the same.
     * Then /send and /broadcast messages to one particular container, then /invoke the GetInfo
     * action of the respective containers to check that the messages were delivered correctly.
     */
    @Test
    public void test5WithContainerId() throws Exception {
        // deploy a second sample container image on platform A
        var image = getSampleContainerImage();
        var con = request(PLATFORM_A, "POST", "/containers", image);
        var newContainerId = result(con);

        try {
            // directed /send and /broadcast to both containers
            var msg1 = Map.of("payload", "\"Message to First Container\"", "replyTo", "");
            var msg2 = Map.of("payload", "\"Message to Second Container\"", "replyTo", "");
            request(PLATFORM_A, "POST", "/broadcast/topic?containerId=" + containerId, msg1);
            request(PLATFORM_A, "POST", "/send/sample1?containerId=" + newContainerId, msg2);
            Thread.sleep(500);

            // directed /invoke of GetInfo to check last messages of first container
            con = request(PLATFORM_A, "POST", "/invoke/GetInfo/sample1?containerId=" + containerId, Map.of());
            var res1 = result(con, Map.class);
            System.out.println(res1);
            Assert.assertEquals(containerId, res1.get(AgentContainerApi.ENV_CONTAINER_ID));
            Assert.assertEquals(msg1.get("payload"), res1.get("lastBroadcast"));
            Assert.assertNotEquals(msg2.get("payload"), res1.get("lastMessage"));

            // directed /invoke of GetInfo to check last messages of second container
            con = request(PLATFORM_A, "POST", "/invoke/GetInfo/sample1?containerId=" + newContainerId, Map.of());
            var res2 = result(con, Map.class);
            System.out.println(res1);
            Assert.assertEquals(newContainerId, res2.get(AgentContainerApi.ENV_CONTAINER_ID));
            Assert.assertNotEquals(msg1.get("payload"), res2.get("lastBroadcast"));
            Assert.assertEquals(msg2.get("payload"), res2.get("lastMessage"));
        } finally {
            // remove the additional sample container image from platform A
            con = request(PLATFORM_A, "DELETE", "/containers/" + newContainerId, null);
            Assert.assertEquals(200, con.getResponseCode());
        }
    }

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
        var message = Map.of("payload", "testBroadcastForward", "replyTo", "doesnotmatter");
        var con = request(PLATFORM_B, "POST", "/broadcast/topic", message);
        Assert.assertEquals(200, con.getResponseCode());
        Thread.sleep(500);

        con = request(PLATFORM_A, "POST", "/invoke/GetInfo/sample1", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        Assert.assertEquals("testBroadcastForward", res.get("lastBroadcast"));
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


    @Test
    public void test7RequestNotify() throws Exception {
        // valid container
        var con1 = request(PLATFORM_A, "POST", "/containers/notify", containerId);
        Assert.assertEquals(200, con1.getResponseCode());

        // valid platform
        var con2 = request(PLATFORM_B, "POST", "/connections/notify", platformABaseUrl);
        Assert.assertEquals(200, con2.getResponseCode());
    }

    @Test
    public void test7RequestInvalidNotify() throws Exception {
        // invalid container
        var con1 = request(PLATFORM_A, "POST", "/containers/notify", "container-does-not-exist");
        Assert.assertEquals(404, con1.getResponseCode());

        // unknown platform
        var con2 = request(PLATFORM_A, "POST", "/connections/notify", "http://platform-does-not-exist.com");
        Assert.assertEquals(404, con2.getResponseCode());

        // invalid platform
        var con3 = request(PLATFORM_A, "POST", "/connections/notify", "invalid-url");
        Assert.assertEquals(400, con3.getResponseCode());
    }

    @Test
    public void test7AddNewActionManualNotify() throws Exception {
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

    @Test
    public void test7AddNewActionAutoNotify() throws Exception {
        // create new agent action
        var con = request(PLATFORM_A, "POST", "/invoke/CreateAction/sample1", Map.of("name", "AnotherTemporaryTestAction", "notify", "true"));
        Assert.assertEquals(200, con.getResponseCode());

        // agent container automatically notified platform of the changes
        con = request(PLATFORM_A, "POST", "/invoke/AnotherTemporaryTestAction/sample1", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        Assert.assertTrue(result(con).contains("AnotherTemporaryTestAction"));
    }

    /**
     * bug in sample-container caused newly spawned agents to hang on invoke. not really API,
     * but sample-container should show "how to do it", so checking that it works now
     */
    @Test
    public void test7SpawnAgentThenInvoke() throws Exception {
        // spawn new sample-agent
        var con = request(PLATFORM_A, "POST", "/invoke/SpawnAgent", Map.of("name", "sample3"));
        Assert.assertEquals(200, con.getResponseCode());
        con = request(PLATFORM_A, "POST", "/containers/notify", containerId);
        Assert.assertEquals(200, con.getResponseCode());

        // invoke action at newly spawned agent
        Assert.assertEquals(200, con.getResponseCode());con = request(PLATFORM_A, "POST", "/invoke/GetInfo/sample3", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        Assert.assertEquals("sample3", res.get("name"));

        // de-register to respore state before test
        con = request(PLATFORM_A, "POST", "/invoke/Deregister/sample3", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        con = request(PLATFORM_A, "POST", "/containers/notify", containerId);
        Assert.assertEquals(200, con.getResponseCode());
    }

    @Test
    public void test8DeregisterAgent() throws Exception {
        // deregister agent "sample2"
        var con = request(PLATFORM_A, "POST", "/invoke/Deregister/sample2", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        con = request(PLATFORM_A, "GET", "/agents", null);
        Assert.assertEquals(200, con.getResponseCode());
        var agentsList = result(con, List.class);
        Assert.assertEquals(2, agentsList.size());

        // notify --> agent should no longer be known
        con = request(PLATFORM_A, "POST", "/containers/notify", containerId);
        Assert.assertEquals(200, con.getResponseCode());
        con = request(PLATFORM_A, "GET", "/agents", null);
        Assert.assertEquals(200, con.getResponseCode());
        agentsList = result(con, List.class);
        Assert.assertEquals(1, agentsList.size());
    }

    /**
     * use sample-agent's de-register action to kill the agent, without notifying the parent
     * platform, then call an action that no longer exists in the container
     */
    @Test
    public void test8InvokeAfterKillAgent() throws Exception {
        // invoke de-register to stop the agent (but it does not notify the parent platform)
        var con = request(PLATFORM_A, "POST", "/invoke/Deregister/sample1", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        // invoke again, causing container to raise 404 and platform has to handle it
        con = request(PLATFORM_A, "POST", "/invoke/GetInfo/sample1", Map.of());
        Assert.assertEquals(502, con.getResponseCode());
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
    public void testXConnectUnknown() throws Exception {
        var con = request(PLATFORM_A, "POST", "/connections", "http://flsflsfsjfkj.com");
        Assert.assertEquals(502, con.getResponseCode());
    }

    @Test
    public void testXConnectInvalid() throws Exception {
        var con = request(PLATFORM_A, "POST", "/connections", "not a valid url");
        Assert.assertEquals(400, con.getResponseCode());
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

}
