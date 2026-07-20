# Model Context Protocol (MCP) in OPACA

OPACA supports the **Model Context Protocol (MCP)**, an open standard that allows LLM applications, helper agents, or external clients (like Claude Desktop or Cursor) to easily discover and invoke tools. 

The entry point and configuration for the MCP server in the platform is managed in `PlatformMcpController.java`.

---

## What It Is & What It Does

The MCP implementation acts as a bridge between MCP-compliant clients and the agent containers deployed on the OPACA platform:
1. **Tool Discovery**: It automatically scans all active agent containers and lists their actions as MCP tools. Each tool is named using the pattern `agentId__actionName`.
2. **Schema Translation**: It translates agent parameter definitions (defined in container schemas) into JSON Schema compliant format so LLMs know how to call the tool.
3. **Execution Routing**: When a client requests the execution of a tool (`CallToolRequest`), the server decodes the JSON parameters and forwards the request to OPACA's `AgentsService.invoke`. The result is formatted back into the MCP response standard and returned to the client.

---

## Server Lifecycle: Boot vs. Call

The MCP server is **not** recreated or started on every request. Instead, it behaves as follows:
* **Initialized Once at Boot**: The `McpSyncServer` is created once during application startup by Spring Boot (via `@PostConstruct` initialization).
* **Initial Tool Sync**: As soon as the platform has fully booted up (listening to `ApplicationReadyEvent`), it runs an initial synchronization (`syncTools()`) to register all currently available agent actions as tools.
* **Dynamic Updates**: When containers are deployed, updated, or removed, a `ContainerChangedEvent` is fired. The server listens to this event and runs `syncTools()` to add or remove tools dynamically. It then calls `notifyToolsListChanged()` to alert connected clients to refresh their tool list, all without needing a server restart.

---

## How GET and POST Requests are Handled

The MCP server uses **HTTP Streamable Transport** (Server-Sent Events / SSE) at the `/mcp` endpoint. Because SSE is a one-way channel from server to client, the communication is divided between GET and POST requests:

1. **GET Request (`GET /mcp`)**:
   * The client initiates the connection by making a `GET` request.
   * The server responds with `text/event-stream`, which keeps the connection open.
   * This stream is used by the server to send asynchronous notifications (like when tools change) and to route the responses of requests back to the client.

2. **POST Request (`POST /mcp`)**:
   * Since the SSE stream is read-only for the client, the client must use HTTP `POST` requests to send messages (e.g., initial handshakes, calling a tool, or querying the tool list) back to the server.
   * The server receives the JSON payloads, processes them, and returns responses back over the established SSE connection stream.

---

## Security & Authentication

Access to `/mcp` endpoints is secured using the standard Spring Security resource server setup configured in `SecurityConfiguration.java`:
* If security/authentication is enabled on the platform, both the GET and POST requests for the `/mcp` and `/mcp/**` endpoints require authentication and must possess the `ROLE_USER` role (or higher, via role hierarchy like `ROLE_CONTRIBUTOR` or `ROLE_MANAGER`).
* If security is disabled, all requests are permitted without authentication.

### How to Authenticate from a Client

To connect to a secured OPACA MCP server, clients must attach the `Authorization` header with a valid Keycloak JWT token to both the initial connection GET requests and the subsequent POST requests:

#### Using Spring AI Java MCP SDK
When creating the client's HTTP/SSE transport, use the `.httpRequestCustomizer(...)` method to inject the bearer token dynamically on all requests:

```java
var transport = HttpClientSseClientTransport.builder("http://localhost:8000")
        .sseEndpoint("/mcp")
        .httpRequestCustomizer(request -> request.header("Authorization", "Bearer " + jwtToken))
        .build();

McpSyncClient client = McpClient.sync(transport).build();
client.initialize();
```

#### TypeScript / Python MCP Clients
Most MCP client SDKs support providing headers during transport construction. Ensure that your client transport is built with:
```json
{
  "headers": {
    "Authorization": "Bearer <YOUR_JWT_TOKEN>"
  }
}
```
Spring Security will intercept these requests, validate the token against the configured Keycloak Issuer, convert the token's resource access roles to Spring `GrantedAuthority` representations using `JwtConverter`, and authorize the request details.
