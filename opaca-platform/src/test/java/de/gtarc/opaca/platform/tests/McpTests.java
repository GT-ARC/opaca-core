package de.gtarc.opaca.platform.tests;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.*;
import org.junit.rules.TestName;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static de.gtarc.opaca.platform.tests.TestUtils.*;

/**
 * Tests the PlatformMcpController with a real MCP client using Streamable HTTP/SSE transport.
 * Deploys the sample-container to verify that tools corresponding to the container's actions
 * are registered correctly on the MCP server and can be invoked end-to-end.
 */
public class McpTests {

    private static final int PORT = 8097;
    private static final String PLATFORM_URL = "http://localhost:" + PORT;
    private static ConfigurableApplicationContext platform = null;
    private static String containerId = null;

    private McpSyncClient client;

    @BeforeClass
    public static void setupPlatform() throws Exception {
        // Start platform without requiring Keycloak or authentication
        platform = startPlatform(PORT, false, false, false, 0);
        // Post a real sample agent container to discover its tools
        containerId = postSampleContainer(PLATFORM_URL);
    }

    @AfterClass
    public static void stopPlatform() throws Exception {
        if (containerId != null) {
            try {
                request(PLATFORM_URL, "DELETE", "/containers/" + containerId, null);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        if (platform != null) {
            platform.close();
        }
    }

    @Rule
    public TestName testName = new TestName();

    @Before
    public void printTest() {
        System.out.println(">>> RUNNING TEST McpTests." + testName.getMethodName());
    }

    @Before
    public void createMcpClient() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(PLATFORM_URL)
                .endpoint("/mcp")
                .build();

        client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .build();

        client.initialize();
    }

    @Test
    public void testMcpToolsReturned() throws Exception {
        // Fetch the registered tools list
        McpSchema.ListToolsResult listResult = client.listTools();
        Assert.assertNotNull(listResult);
        List<McpSchema.Tool> tools = listResult.tools();
        Assert.assertNotNull(tools);
        Assert.assertFalse("Tools list should not be empty", tools.isEmpty());

        System.out.println("Discovered MCP Tools: ");
        for (McpSchema.Tool tool : tools) {
            System.out.println("- " + tool.name() + ": " + tool.description());
        }

        // The sample container defines sample1 and sample2 agents with various actions
        boolean hasSample1Add = tools.stream().anyMatch(t -> t.name().equals("sample1__Add"));
        boolean hasSample1GetInfo = tools.stream().anyMatch(t -> t.name().equals("sample1__GetInfo"));
        boolean hasSample2GetInfo = tools.stream().anyMatch(t -> t.name().equals("sample2__GetInfo"));

        Assert.assertTrue("Should discover sample1__Add tool", hasSample1Add);
        Assert.assertTrue("Should discover sample1__GetInfo tool", hasSample1GetInfo);
        Assert.assertTrue("Should discover sample2__GetInfo tool", hasSample2GetInfo);
    }

    @Test
    public void testCallMcpTool() throws Exception {
        // Call the sample1__Add tool with parameters x and y
        CallToolRequest request = CallToolRequest.builder("sample1__Add").arguments(Map.of("x", 25, "y", 17)).build();
        CallToolResult result = client.callTool(request);

        Assert.assertNotNull(result);
        Assert.assertFalse("Tool invocation should run successfully", result.isError());
        List<McpSchema.Content> content = result.content();
        Assert.assertNotNull(content);
        Assert.assertEquals(1, content.size());

        McpSchema.Content c = content.get(0);
        Assert.assertTrue("Content should be TextContent", c instanceof McpSchema.TextContent);
        String text = ((McpSchema.TextContent) c).text();
        System.out.println("sample1__Add result: " + text);
        // 25 + 17 = 42
        Assert.assertTrue("Addition result should be 42", text.contains("42"));
    }

    @Test
    public void testContainerRemovalSyncsTools() throws Exception {
        // Verify tools are initially present
        List<McpSchema.Tool> toolsBefore = client.listTools().tools();
        Assert.assertTrue(toolsBefore.stream().anyMatch(t -> t.name().equals("sample1__Add")));

        // Delete the container to trigger tool removal
        var deleteResponse = request(PLATFORM_URL, "DELETE", "/containers/" + containerId, null);
        Assert.assertEquals(200, deleteResponse.getResponseCode());

        // A short delay to allow background sync / event processing
        Thread.sleep(1000);

        // Fetch the updated tools list
        List<McpSchema.Tool> toolsAfter = client.listTools().tools();
        System.out.println("Tools count after container deletion: " + toolsAfter.size());
        Assert.assertFalse("Tools should not include sample1__Add after container is removed",
                toolsAfter.stream().anyMatch(t -> t.name().startsWith("sample1__")));

        // Redeploy container for cleanup in AfterClass
        containerId = postSampleContainer(PLATFORM_URL);
        Thread.sleep(1000);
    }
}
