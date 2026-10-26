package de.gtarc.opaca.platform.tests;

import de.gtarc.opaca.platform.Application;
import de.gtarc.opaca.platform.services.LLMService;
import org.junit.*;
import org.junit.rules.TestName;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static de.gtarc.opaca.platform.tests.TestUtils.*;

/**
 * Tests the A2A endpoints by starting a platform in the test itself,
 * fetching the agent card, and posting a query with the LLM part mocked away.
 */
public class A2ATests {

    private static final int PORT = 8098;
    private static final String PLATFORM_URL = "http://localhost:" + PORT;
    private static ConfigurableApplicationContext platform = null;

    @BeforeClass
    public static void setupPlatform() {
        platform = startPlatformWithMockLLM(PORT);
    }

    @AfterClass
    public static void stopPlatform() {
        if (platform != null) {
            platform.close();
        }
    }

    @Rule
    public TestName testName = new TestName();

    @Before
    public void printTest() {
        System.out.println(">>> RUNNING TEST A2ATests." + testName.getMethodName());
    }

    /**
     * Test that the agent card is correctly returned at the well-known endpoint.
     */
    @Test
    public void testAgentCard() throws Exception {
        var con = request(PLATFORM_URL, "GET", "/.well-known/agent-card.json", null);
        Assert.assertEquals(200, con.getResponseCode());
        String json = result(con);
        Assert.assertTrue("Agent card should contain agent name", json.contains("OPACA Agent"));
        Assert.assertTrue("Agent card should contain version", json.contains("1.0.0"));
        Assert.assertTrue("Agent card should contain skills", json.contains("opaca_actions"));
    }

    /**
     * Test sending a message via the A2A message:send endpoint.
     * The LLM is mocked, so the response should be the mock response.
     */
    @Test
    public void testSendMessage() throws Exception {
        String body = "{\n"
                + "  \"message\": {\n"
                + "    \"messageId\": \"test-message-001\",\n"
                + "    \"role\": \"ROLE_USER\",\n"
                + "    \"parts\": [\n"
                + "      {\n"
                + "        \"text\": \"What actions can you perform?\"\n"
                + "      }\n"
                + "    ]\n"
                + "  }\n"
                + "}";

        HttpURLConnection con = (HttpURLConnection) new URI(PLATFORM_URL + "/a2a/message:send").toURL().openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("A2A-Version", "1.0");
        con.setDoOutput(true);
        con.connect();

        try (var os = con.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        Assert.assertEquals(200, con.getResponseCode());
        String response = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))
                .lines().collect(Collectors.joining("\n"));

        Assert.assertTrue("Response should contain taskId", response.contains("\"taskId\""));
        Assert.assertTrue("Response should contain mock answer", response.contains("Mock LLM response"));
    }

    /**
     * Test sending a message with a different messageId to verify the A2A task handling
     * creates a separate task for each message.
     */
    @Test
    public void testSendMessageDifferentId() throws Exception {
        String body = "{\n"
                + "  \"message\": {\n"
                + "    \"messageId\": \"test-message-002\",\n"
                + "    \"role\": \"ROLE_USER\",\n"
                + "    \"parts\": [\n"
                + "      {\n"
                + "        \"text\": \"Hello\"\n"
                + "      }\n"
                + "    ]\n"
                + "  }\n"
                + "}";

        HttpURLConnection con = (HttpURLConnection) new URI(PLATFORM_URL + "/a2a/message:send").toURL().openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("A2A-Version", "1.0");
        con.setDoOutput(true);
        con.connect();

        try (var os = con.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        Assert.assertEquals(200, con.getResponseCode());
        String response = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))
                .lines().collect(Collectors.joining("\n"));
        Assert.assertTrue("Response should contain taskId", response.contains("\"taskId\""));
        Assert.assertTrue("Response should contain mock answer", response.contains("Mock LLM response"));
    }

    /**
     * Start the platform with a mocked LLMService bean.
     * Uses the real Application class but adds a test config that provides
     * a @Primary mock LLMService, overriding the real one.
     */
    private static ConfigurableApplicationContext startPlatformWithMockLLM(int port) {
        var springApp = new org.springframework.boot.SpringApplication();
        springApp.addPrimarySources(java.util.List.of(Application.class, TestConfig.class));
        return springApp.run("--server.port=" + port);
    }

    /**
     * Test configuration that provides a mocked LLMService, replacing the real one.
     * The mock is marked @Primary so it takes precedence over the real LLMService bean.
     */
    @ComponentScan(excludeFilters = @ComponentScan.Filter(
            type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
            classes = LLMService.class
    ))
    static class TestConfig {

        @Primary
        @Bean
        LLMService llmService() {
            return new LLMService() {
                @Override
                public QueryResponse query(String message, String userName) {
                    return new QueryResponse("Mock LLM response to: " + message, java.util.Collections.emptyList());
                }
            };
        }
    }
}
