package de.gtarc.opaca.platform.tests;

import de.gtarc.opaca.api.AgentContainerApi;
import de.gtarc.opaca.model.AgentContainer;
import de.gtarc.opaca.model.AgentDescription;
import de.gtarc.opaca.model.RuntimePlatform;
import de.gtarc.opaca.platform.Application;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.gtarc.opaca.platform.tests.TestUtils.*;

/**
 * This module is for tests that require the sample agent container to be deployed for e.g. testing invoking actions.
 * The tests should not deploy other container, or if so, those should be removed after the test. In general, all the
 * tests should be (and remain) independent of each other, able to run individually or in any order.
 * At the start, a single runtime platform is started and a sample-agent-container is deployed. That state should be
 * maintained after each test.
 */
public class ContainerTests {

    private static final int PLATFORM_PORT = 8003;

    private static final String PLATFORM_URL = "http://localhost:" + PLATFORM_PORT;

    private static ConfigurableApplicationContext platform = null;

    private static String containerId = null;

    @BeforeClass
    public static void setupPlatform() throws IOException {
        platform = SpringApplication.run(Application.class,
                "--server.port=" + PLATFORM_PORT);
        containerId = postSampleContainer(PLATFORM_URL);
    }

    @AfterClass
    public static void stopPlatform() {
        platform.close();
    }

    @After
    public void checkInvariant() throws Exception {
        var con1 = request(PLATFORM_URL, "GET", "/info", null);
        var res1 = result(con1, RuntimePlatform.class);
        Assert.assertEquals(1, res1.getContainers().size());
        var agents = res1.getContainers().get(0).getAgents();
        Assert.assertEquals(2, agents.size());
    }

    /**
     * get container info
     */
    @Test
    public void testGetContainerInfo() throws Exception {
        var con = request(PLATFORM_URL, "GET", "/containers/" + containerId, null);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, AgentContainer.class);
        Assert.assertEquals(containerId, res.getContainerId());
    }

    @Test
    public void testGetAgents() throws Exception {
        var con = request(PLATFORM_URL, "GET", "/agents", null);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, List.class);
        Assert.assertEquals(2, res.size());
    }

    @Test
    public void testGetAgent() throws Exception {
        var con = request(PLATFORM_URL, "GET", "/agents/sample1", null);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, AgentDescription.class);
        Assert.assertEquals("sample1", res.getAgentId());
    }

    @Test
    public void testGetUnknownAgent() throws Exception {
        var con = request(PLATFORM_URL, "GET", "/agents/unknown", null);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con);
        Assert.assertTrue(res.isEmpty());
    }

    /**
     * call invoke, check result
     */
    @Test
    public void testInvokeAction() throws Exception {
        var params = Map.of("x", 23, "y", 42);
        var con = request(PLATFORM_URL, "POST", "/invoke/Add", params);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Integer.class);
        Assert.assertEquals(65L, res.longValue());
    }

    @Test
    public void testGetStream() throws Exception {
        var con = request(PLATFORM_URL, "GET", "/stream/GetStream", null);
        Assert.assertEquals(200, con.getResponseCode());
        var inputStream = con.getInputStream();
        var bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        var response = bufferedReader.readLine();
        Assert.assertEquals("{\"key\":\"value\"}", response);
    }

    @Test
    public void testPostStream() throws Exception {
        String jsonInput = "{\"key\":\"value\"}";
        byte[] jsonData = jsonInput.getBytes(StandardCharsets.UTF_8);
        var responseCode = streamRequest(PLATFORM_URL, "POST", "/stream/PostStream/sample1", jsonData);
        Assert.assertEquals(200, responseCode);

        var con = request(PLATFORM_URL, "POST", "/invoke/GetInfo/sample1", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);

        // TODO for some reason, this wraps the input into "[...]", might be solved when decoupling API from Rest Controller...
        // Assert.assertEquals(jsonInput, res.get("lastPostedStream"));
    }

    /**
     * call invoke with agent, check result
     */
    @Test
    public void testInvokeAgentAction() throws Exception {
        for (String name : List.of("sample1", "sample2")) {
            var con = request(PLATFORM_URL, "POST", "/invoke/GetInfo/" + name, Map.of());
            var res = result(con, Map.class);
            Assert.assertEquals(name, res.get("name"));
        }
    }

    @Test
    public void testInvokeFail() throws Exception {
        var con = request(PLATFORM_URL, "POST", "/invoke/Fail", Map.of());
        Assert.assertEquals(502, con.getResponseCode());
        var msg = error(con);
        Assert.assertTrue(msg.contains("Action Failed (as expected)"));
    }

    /**
     * test that action invocation fails if it does not respond within the specified time
     * TODO this is not ideal yet... the original error may contain a descriptive message that is lost
     */
    @Test
    public void testInvokeTimeout() throws Exception {
        var params = Map.of("message", "timeout-test", "sleep_seconds", 5);
        var con = request(PLATFORM_URL, "POST", "/invoke/DoThis?timeout=2", params);
        Assert.assertEquals(502, con.getResponseCode());
    }

    /**
     * invoke action with mismatched/missing parameters
     * TODO case of missing parameter could also be handled by platform, resulting in 422 error
     *  -> or 404, i.e. "platform cant find container/agent/action/params"
     */
    @Test
    public void testInvokeParamMismatch() throws Exception {
        var con = request(PLATFORM_URL, "POST", "/invoke/DoThis",
                Map.of("message", "missing 'sleep_seconds' parameter!"));
        Assert.assertEquals(404, con.getResponseCode());
    }

    @Test
    public void testArgumentValidation() throws Exception {

        // missing params
        var con = request(PLATFORM_URL, "POST", "/invoke/ValidatorTest", Map.of());
        Assert.assertEquals(404, con.getResponseCode());

        // redundant params
        con = request(PLATFORM_URL, "POST", "/invoke/ValidatorTest", Map.of(
                "car", Map.of(),
                "listOfLists", List.of(),
                "redundantParam", "text"));
        Assert.assertEquals(404, con.getResponseCode());

        // invalid object
        con = request(PLATFORM_URL, "POST", "/invoke/ValidatorTest", Map.of(
                "car", new ContainerTests.Car(), // missing required attributes
                "listOfLists", List.of(List.of(1, 2), List.of(3, 4))));
        Assert.assertEquals(404, con.getResponseCode());

        // invalid arg for defined type (string instead of int)
        con = request(PLATFORM_URL, "POST", "/invoke/Add", Map.of(
                "x", "42",
                "y", 5));
        Assert.assertEquals(404, con.getResponseCode());

        // invalid arg for defined type (string instead of bool)
        con = request(PLATFORM_URL, "POST", "/invoke/CreateAction", Map.of(
                "name", "InvalidAction",
                "notify", "true"));
        Assert.assertEquals(404, con.getResponseCode());

        // invalid arg for defined type (number/double instead of int)
        con = request(PLATFORM_URL, "POST", "/invoke/CreateAction", Map.of(
                "x", 42,
                "y", 5.5));
        Assert.assertEquals(404, con.getResponseCode());

        // invalid list
        con = request(PLATFORM_URL, "POST", "/invoke/ValidatorTest", Map.of(
                "car", new Car("testModel", List.of("1", "b", "test"),
                        true, 1444),
                "listOfLists", List.of(Map.of("test1", "test2"), 2, "")));
        Assert.assertEquals(404, con.getResponseCode());

        // faulty desk (param x of nested object position is string instead of int)
        con = request(PLATFORM_URL, "POST", "/invoke/ValidatorTest", Map.of(
                "car", new Car("testModel", List.of("1", "b", "test"),
                        true, 1444),
                "listOfLists", List.of(List.of(1, 2), List.of(3, 4)),
                "desk", Map.of("deskId", 123, "position", Map.of("x", "5", "y", 3))));
        Assert.assertEquals(404, con.getResponseCode());

        // all valid, including desk
        con = request(PLATFORM_URL, "POST", "/invoke/ValidatorTest", Map.of(
                "car", new Car("testModel", List.of("1", "b", "test"),
                        true, 1444),
                "listOfLists", List.of(List.of(1, 2), List.of(3, 4)),
                "desk", new Desk(123, "test name", "test description", new Desk.Position(42, 5))));
        Assert.assertEquals(200, con.getResponseCode());

        // passing int for param type "number" -> valid
        con = request(PLATFORM_URL, "POST", "/invoke/ValidatorTest", Map.of(
                "car", new ContainerTests.Car("testModel", List.of("1", "b", "test"),
                        true, 1444),
                "listOfLists", List.of(List.of(1, 2), List.of(3, 4)),
                "decimal", 42));
        Assert.assertEquals(200, con.getResponseCode());

        // all valid
        con = request(PLATFORM_URL, "POST", "/invoke/ValidatorTest", Map.of(
                "car", new ContainerTests.Car("testModel", List.of("1", "b", "test"),
                        true, 1444),
                "listOfLists", List.of(List.of(1, 2), List.of(3, 4))));
        // System.out.println(result(con));
        Assert.assertEquals(200, con.getResponseCode());

    }

    /**
     * try to invoke unknown action
     * -> 404 (not found)
     */
    @Test
    public void testUnknownAction() throws Exception {
        var con = request(PLATFORM_URL, "POST", "/invoke/UnknownAction", Map.of());
        Assert.assertEquals(404, con.getResponseCode());

        con = request(PLATFORM_URL, "POST", "/invoke/Add/unknownagent", Map.of());
        Assert.assertEquals(404, con.getResponseCode());
    }

    /**
     * call send, check that it arrived via another invoke
     */
    @Test
    public void testSend() throws Exception {
        var message = Map.of("payload", "testMessage", "replyTo", "doesnotmatter");
        var con = request(PLATFORM_URL, "POST", "/send/sample1", message);
        Assert.assertEquals(200, con.getResponseCode());

        con = request(PLATFORM_URL, "POST", "/invoke/GetInfo/sample1", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        Assert.assertEquals("testMessage", res.get("lastMessage"));
    }

    /**
     * try to send message to unknown agent
     * -> 404 (not found)
     */
    @Test
    public void testUnknownSend() throws Exception {
        var message = Map.of("payload", "testMessage", "replyTo", "doesnotmatter");
        var con = request(PLATFORM_URL, "POST", "/send/unknownagent", message);
        Assert.assertEquals(404, con.getResponseCode());
    }

    /**
     * call broadcast, check that it arrived via another invoke
     */
    @Test
    public void testBroadcast() throws Exception {
        var message = Map.of("payload", "testBroadcast", "replyTo", "doesnotmatter");
        var con = request(PLATFORM_URL, "POST", "/broadcast/topic", message);
        Assert.assertEquals(200, con.getResponseCode());

        con = request(PLATFORM_URL, "POST", "/invoke/GetInfo/sample1", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        Assert.assertEquals("testBroadcast", res.get("lastBroadcast"));
    }

    /**
     * test that container's /info route can be accessed via that port
     */
    @Test
    public void testApiPort() throws Exception {
        var con = request(PLATFORM_URL, "GET", "/containers/" + containerId, null);
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
    public void testExtraPortTCP() throws Exception {
        var con = request(PLATFORM_URL, "GET", "/containers/" + containerId, null);
        var res = result(con, AgentContainer.class).getConnectivity();

        var url = String.format("%s:%s", res.getPublicUrl(), "8888");
        con = request(url, "GET", "/", null);
        Assert.assertEquals(200, con.getResponseCode());
        Assert.assertEquals("It Works!", result(con));
    }

    @Test
    public void testExtraPortUDP() throws Exception {
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
     * TODO check somehow that notify worked
     */
    @Test
    public void testContainerNotify() throws Exception {
        var con1 = request(PLATFORM_URL, "POST", "/containers/notify", containerId);
        Assert.assertEquals(200, con1.getResponseCode());
    }

    /**
     * tries to notify about non-existing container
     */
    @Test
    public void testInvalidContainerNotify() throws Exception {
        var con1 = request(PLATFORM_URL, "POST", "/containers/notify", "container-does-not-exist");
        Assert.assertEquals(404, con1.getResponseCode());
    }

    /**
     * test that connectivity info is still there after /notify
     */
    @Test
    public void testNotifyConnectivity() throws Exception {
        request(PLATFORM_URL, "POST", "/containers/notify", containerId);
        var con = request(PLATFORM_URL, "GET", "/containers/" + containerId, null);
        var res = result(con, AgentContainer.class);
        Assert.assertNotNull(res.getConnectivity());
    }

    /**
     * Deploy an additional sample-container on the same platform, so there are two of the same.
     * Then /send and /broadcast messages to one particular container, then /invoke the GetInfo
     * action of the respective containers to check that the messages were delivered correctly.
     */
    @Test
    public void testSendWithContainerId() throws Exception {
        // deploy a second sample container image on platform A
        var image = getSampleContainerImage();
        var con = request(PLATFORM_URL, "POST", "/containers", image);
        var newContainerId = result(con);

        try {
            // directed /send and /broadcast to both containers
            var msg1 = Map.of("payload", "\"Message to First Container\"", "replyTo", "");
            var msg2 = Map.of("payload", "\"Message to Second Container\"", "replyTo", "");
            request(PLATFORM_URL, "POST", "/broadcast/topic?containerId=" + containerId, msg1);
            request(PLATFORM_URL, "POST", "/send/sample1?containerId=" + newContainerId, msg2);
            Thread.sleep(500);

            // directed /invoke of GetInfo to check last messages of first container
            con = request(PLATFORM_URL, "POST", "/invoke/GetInfo/sample1?containerId=" + containerId, Map.of());
            var res1 = result(con, Map.class);
            System.out.println(res1);
            Assert.assertEquals(containerId, res1.get(AgentContainerApi.ENV_CONTAINER_ID));
            Assert.assertEquals(msg1.get("payload"), res1.get("lastBroadcast"));
            Assert.assertNotEquals(msg2.get("payload"), res1.get("lastMessage"));

            // directed /invoke of GetInfo to check last messages of second container
            con = request(PLATFORM_URL, "POST", "/invoke/GetInfo/sample1?containerId=" + newContainerId, Map.of());
            var res2 = result(con, Map.class);
            System.out.println(res1);
            Assert.assertEquals(newContainerId, res2.get(AgentContainerApi.ENV_CONTAINER_ID));
            Assert.assertNotEquals(msg1.get("payload"), res2.get("lastBroadcast"));
            Assert.assertEquals(msg2.get("payload"), res2.get("lastMessage"));
        } finally {
            // remove the additional sample container image from platform A
            con = request(PLATFORM_URL, "DELETE", "/containers/" + newContainerId, null);
            Assert.assertEquals(200, con.getResponseCode());
        }
    }

    /**
     * TODO keep testing this? notify param in createAction might be obsolete.
     *  we could of course still keep it specifically for the purpose of this test.
     */
    @Test
    public void testAddNewActionAutoNotify() throws Exception {
        // create new agent action
        var con = request(PLATFORM_URL, "POST", "/invoke/CreateAction/sample1", Map.of("name", "AnotherTemporaryTestAction", "notify", true));
        Assert.assertEquals(200, con.getResponseCode());

        // agent container automatically notified platform of the changes
        con = request(PLATFORM_URL, "POST", "/invoke/AnotherTemporaryTestAction/sample1", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        Assert.assertTrue(result(con).contains("AnotherTemporaryTestAction"));
    }

    /**
     * now combines the tests "SpawnThenInvoke", "Deregister" and "InvokeAfterKill"
     */
    @Test
    public void testSpawnInvokeDeregister() throws Exception {
        // save agent count
        var con = request(PLATFORM_URL, "GET", "/agents", null);
        var agentCount = result(con, List.class).size();

        // spawn new sample-agent
        con = request(PLATFORM_URL, "POST", "/invoke/SpawnAgent", Map.of("name", "sample3"));
        Assert.assertEquals(200, con.getResponseCode());
        con = request(PLATFORM_URL, "POST", "/containers/notify", containerId);
        Assert.assertEquals(200, con.getResponseCode());
        Thread.sleep(1000);

        // invoke action at newly spawned agent
        con = request(PLATFORM_URL, "POST", "/invoke/GetInfo/sample3", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        Assert.assertEquals("sample3", res.get("name"));

        // deregister previously spawned agent "sample3"
        con = request(PLATFORM_URL, "POST", "/invoke/Deregister/sample3", Map.of());
        Assert.assertEquals(200, con.getResponseCode());

        // agent count still at +1 since container has not yet notified platform
        con = request(PLATFORM_URL, "GET", "/agents", null);
        Assert.assertEquals(200, con.getResponseCode());
        var agentsList = result(con, List.class);
        Assert.assertEquals(agentCount + 1, agentsList.size());

        // invoke again, causing container to raise 404 and platform has to handle it
        con = request(PLATFORM_URL, "POST", "/invoke/GetInfo/sample3", Map.of());
        Assert.assertEquals(502, con.getResponseCode());

        // notify --> agent should no longer be known
        con = request(PLATFORM_URL, "POST", "/containers/notify", containerId);
        Assert.assertEquals(200, con.getResponseCode());
        con = request(PLATFORM_URL, "GET", "/agents", null);
        Assert.assertEquals(200, con.getResponseCode());
        agentsList = result(con, List.class);
        Assert.assertEquals(agentCount, agentsList.size());
    }

    /**
     * invoke long-running action at two agents in parallel; the agents may be "busy" until the first action is through
     * (and indeed they are in the JIAC VI reference impl), but the ContainerAgent (and of course the Swagger UI) are
     * still responsive and can take on tasks for other agents.
     */
    @Test
    public void testInvokeNonblocking() throws Exception {
        long start = System.currentTimeMillis();
        List<Thread> threads = Stream.of("sample1", "sample2")
                .map(agent -> new Thread(() -> {
                    try {
                        var con = request(PLATFORM_URL, "POST", "/invoke/DoThis/" + agent + "?timeout=10",
                                Map.of("message", "", "sleep_seconds", 5));
                        Assert.assertEquals(200, con.getResponseCode());
                    } catch (Exception e) {
                        Assert.fail(e.getMessage());
                    }
                })).collect(Collectors.toList());

        // handle assertion errors in created threads
        var noErrorsDetected = new AtomicBoolean(true);
        for (Thread t : threads) t.setUncaughtExceptionHandler((thread, throwable) -> {
            if (throwable instanceof AssertionError) {
                var message = String.format("AssertionError in thead %s: %s", thread.getName(), throwable.getMessage());
                System.out.println(message);
                noErrorsDetected.set(false);
            }
        });

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();
        System.out.println(System.currentTimeMillis() - start);
        Assert.assertTrue("At least 1 of the 2 requests failed.", noErrorsDetected.get());
        Assert.assertTrue(System.currentTimeMillis() - start < 8 * 1000);
    }

    /**
     * if auth is disabled, no token should be passed to container
     */
    @Test
    public void testAuthNoToken() throws Exception {
        var con = request(PLATFORM_URL, "POST", "/invoke/GetInfo", Map.of());
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Map.class);
        Assert.assertEquals("", res.get(AgentContainerApi.ENV_TOKEN));
    }

    /**
     * classes for testing the argument validator
     */

    @Data @AllArgsConstructor @NoArgsConstructor
    private static class Car {
        String model;
        List<String> passengers;
        Boolean isFunctional;
        Integer constructionYear;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    private static class Desk {
        Integer deskId;
        String name;
        String description;
        Position position;
        @Data @AllArgsConstructor @NoArgsConstructor
        private static class Position {
            Integer x;
            Integer y;
        }
    }

}
