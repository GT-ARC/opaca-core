package de.gtarc.opaca.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import de.gtarc.opaca.api.AgentContainerApi;
import de.gtarc.opaca.api.RuntimePlatformApi;
import de.gtarc.opaca.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Implementation of the API forwarding to the REST services at a specific base URL.
 * Can be used for e.g. calling routes of a connected Runtime Platform, or a container's
 * parent Runtime Platform, or just for testing.
 */
public class ApiProxy implements RuntimePlatformApi, AgentContainerApi {

    public final String baseUrl;
    private final RestHelper client;

    @Deprecated
    public ApiProxy(String baseUrl) {
        this(baseUrl, null, null, null);
    }

    public ApiProxy(String baseUrl, String senderId, String token) {
        this(baseUrl, senderId, token, null);
    }

    public ApiProxy(String baseUrl, String senderId, String token, Integer timeout) {
        this.baseUrl = baseUrl;
        this.client = new RestHelper(baseUrl, senderId, token, timeout, RestHelper.Encoding.JSON);
    }

    // INFO ROUTES

    @Override
    public RuntimePlatform getPlatformInfo() throws IOException {
        return client.get("/info", RuntimePlatform.class);
    }

    @Override
    public List<AgentDescription> getAllAgents() throws IOException {
        return client.get("/agents?includeConnected=true", new TypeReference<>(){});
    }

    @Override
    public Map<String, ?> getPlatformConfig() throws IOException {
        return client.get("/config", new TypeReference<>(){});
    }

    @Override
    public List<Event> getHistory() throws IOException {
        return client.get("/history", new TypeReference<>(){});
    }

    @Override
    public AgentContainer getContainerInfo() throws IOException {
        return client.get("/info", AgentContainer.class);
    }

    // AUTHENTICATION

    @Override
    public String login(Login loginParams) throws IOException {
        // token should be raw string
        return client.readStream(client.request("POST", "/login", loginParams));
    }

    @Override
    public String renewToken() throws IOException {
        return client.readStream(client.request("GET", "/token", null));
    }

    // AGENT ROUTES

    @Override
    public List<AgentDescription> getAgents() throws IOException {
        return client.get("/agents", new TypeReference<>(){});
    }

    @Override
    public AgentDescription getAgent(String agentId) throws IOException {
        var path = String.format("/agents/%s", agentId);
        return client.get(path, AgentDescription.class);
    }

    @Override
    public void send(String agentId, Message message, String containerId, boolean forward) throws IOException {
        var path = String.format("/send/%s?%s", agentId, buildQuery(containerId, forward, null));
        client.post(path, message, null);
    }

    @Override
    public void broadcast(String channel, Message message, String containerId, boolean forward) throws IOException {
        var path = String.format("/broadcast/%s?%s", channel, buildQuery(containerId, forward, null));
        client.post(path, message, null);
    }

    @Override
    public JsonNode invoke(String action, Map<String, JsonNode> parameters, String agentId, int timeout, String containerId, boolean forward) throws IOException {
        var path = agentId == null
                ? String.format("/invoke/%s?%s", action, buildQuery(containerId, forward, timeout))
                : String.format("/invoke/%s/%s?%s", action, agentId, buildQuery(containerId, forward, timeout));
        return client.post(path, parameters, JsonNode.class);
    }

    @Override
    public InputStream getStream(String stream, String agentId, String containerId, boolean forward) throws IOException {
        var path = agentId == null
                ? String.format("/stream/%s?%s", stream, buildQuery(containerId, forward, null))
                : String.format("/stream/%s/%s?%s", stream, agentId, buildQuery(containerId, forward, null));
        return client.request("GET", path, null);
    }

    @Override
    public void postStream(String stream, byte[] inputStream, String agentId, String containerId, boolean forward) throws IOException {
        var path = agentId == null
                ? String.format("/stream/%s?%s", stream, buildQuery(containerId, forward, null))
                : String.format("/stream/%s/%s?%s", stream, agentId, buildQuery(containerId, forward, null));
        client.postStream(path, inputStream);
    }

    // CONTAINER ROUTES

    @Override
    public String addContainer(PostAgentContainer container, int timeout) throws IOException {
        var query = buildQuery(Map.of("timeout", timeout));
        return client.post("/containers?" + query, container, String.class);
    }

    @Override
    public String updateContainer(PostAgentContainer container, int timeout) throws IOException {
        var query = buildQuery(Map.of("timeout", timeout));
        return client.put("/containers?" + query, container, String.class);
    }

    @Override
    public List<AgentContainer> getContainers() throws IOException {
        return client.get("/containers", new TypeReference<>(){});
    }

    @Override
    public AgentContainer getContainer(String containerId) throws IOException {
        var path = String.format("/containers/%s", containerId);
        return client.get(path, AgentContainer.class);
    }

    @Override
    public boolean removeContainer(String containerId) throws IOException {
        var path = String.format("/containers/%s", containerId);
        return client.delete(path, null, Boolean.class);
    }

    // CONNECTING ROUTES

    @Override
    public boolean connectPlatform(LoginConnection loginConnection) throws IOException {
        return client.post("/connections", loginConnection, Boolean.class);
    }

    @Override
    public List<String> getConnections() throws IOException {
        return client.get("/connections", new TypeReference<>(){});
    }

    @Override
    public boolean disconnectPlatform(String url) throws IOException {
        return client.delete("/connections", url, Boolean.class);
    }

    @Override
    public boolean notifyUpdateContainer(String containerId) throws IOException {
        return client.post("/containers/notify", containerId, Boolean.class);
    }

    @Override
    public boolean notifyUpdatePlatform(String platformUrl) throws IOException {
        return client.post("/connections/notify", platformUrl, Boolean.class);
    }

    /**
     * Helper method for building Query string (without initial ?); will be more useful when there are more.
     */
    private String buildQuery(String containerId, Boolean forward, Integer timeout) {
        Map<String, Object> params = new HashMap<>(); // Map.of does not work with nullable values
        params.put("containerId", containerId);
        params.put("forward", forward);
        params.put("timeout", timeout);
        return buildQuery(params);
    }

    private String buildQuery(Map<String, Object> params) {
        return params.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> String.format("%s=%s", e.getKey(), e.getValue()))
                .collect(Collectors.joining("&"));
    }
}
