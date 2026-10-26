package de.gtarc.opaca.platform.tests;

import de.gtarc.opaca.platform.Application;
import de.gtarc.opaca.platform.services.LLMService;
import org.junit.*;
import org.junit.rules.TestName;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        var body = Map.of(
                "message", Map.of(
                        "messageId", "test-message-001",
                        "role", "ROLE_USER",
                        "parts", List.of(Map.of(
                                "text", "What actions can you perform?"
                        ))
                ));
        var header = Map.of("A2A-Version", "1.0");
        var con = requestWithHeader(PLATFORM_URL, "POST","/a2a/message:send", body, header);
        Assert.assertEquals(200, con.getResponseCode());
        String response = result(con);
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
                    return new QueryResponse("Mock LLM response to: " + message, Collections.emptyList());
                }
            };
        }
    }
}
