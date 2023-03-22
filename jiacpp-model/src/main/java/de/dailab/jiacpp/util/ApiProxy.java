package de.dailab.jiacpp.util;

import com.fasterxml.jackson.databind.JsonNode;
import de.dailab.jiacpp.api.AgentContainerApi;
import de.dailab.jiacpp.api.RuntimePlatformApi;
import de.dailab.jiacpp.model.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the API forwarding to the REST services at a specific base URL.
 * Can be used for e.g. calling routes of a connected Runtime Platform, or a container's
 * parent Runtime Platform, or just for testing.
 */
public class ApiProxy implements RuntimePlatformApi, AgentContainerApi {

    public final String baseUrl;
    private final RestHelper client;

    public ApiProxy(String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = new RestHelper(baseUrl);
    }

    // INFO ROUTES

    @Override
    public RuntimePlatform getPlatformInfo() throws IOException {
        System.out.println("GET INFO");
        return client.get("/info", RuntimePlatform.class);
    }

    @Override
    public AgentContainer getContainerInfo() throws IOException {
        System.out.println("GET INFO");
        return client.get("/info", AgentContainer.class);
    }

    // AGENT ROUTES

    @SuppressWarnings({"unchecked"})
    @Override
    public List<AgentDescription> getAgents() throws IOException {
        System.out.println("GET AGENTS");
        return client.get("/agents", List.class);
    }

    @Override
    public AgentDescription getAgent(String agentId) throws IOException {
        System.out.println("GET AGENT");
        System.out.println(agentId);
        return client.get(String.format("/agents/%s", agentId), AgentDescription.class);
    }

    @Override
    public void send(String agentId, Message message, boolean forward) throws IOException {
        System.out.println("SEND");
        System.out.println(agentId);
        System.out.println(message);
        System.out.println(forward);
        client.post(String.format("/send/%s?forward=%s", agentId, forward), message, null);
    }

    @Override
    public void broadcast(String channel, Message message, boolean forward) throws IOException {
        System.out.println("BROADCAST");
        System.out.println(channel);
        System.out.println(message);
        System.out.println(forward);
        client.post(String.format("/broadcast/%s?forward=%s", channel, forward), message, null);
    }

    @Override
    public JsonNode invoke(String action, Map<String, JsonNode> parameters, boolean forward) throws IOException {
        System.out.println("INVOKE");
        System.out.println(action);
        System.out.println(parameters);
        System.out.println(forward);
        return client.post(String.format("/invoke/%s?forward=%s", action, forward), parameters, JsonNode.class);
    }

    @Override
    public JsonNode invoke(String agentId, String action, Map<String, JsonNode> parameters, boolean forward) throws IOException {
        System.out.println("INVOKE");
        System.out.println(action);
        System.out.println(agentId);
        System.out.println(parameters);
        System.out.println(forward);
        return client.post(String.format("/invoke/%s/%s?forward=%s", action, agentId, forward), parameters, JsonNode.class);
    }

    // CONTAINER ROUTES

    @Override
    public String addContainer(AgentContainerImage container) throws IOException {
        System.out.println("ADD CONTAINER");
        System.out.println(container);
        return client.post("/containers", container, String.class);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public List<AgentContainer> getContainers() throws IOException {
        System.out.println("GET CONTAINERS");
        return client.get("/containers", List.class);
    }

    @Override
    public AgentContainer getContainer(String containerId) throws IOException {
        System.out.println("GET CONTAINER");
        System.out.println(containerId);
        return client.get(String.format("/containers/%s", containerId), AgentContainer.class);
    }

    @Override
    public boolean removeContainer(String containerId) throws IOException {
        System.out.println("REMOVE  CONTAINER");
        System.out.println(containerId);
        return client.delete(String.format("/containers/%s", containerId), null, Boolean.class);
    }

    // CONNECTING ROUTES

    @Override
    public boolean connectPlatform(String url) throws IOException {
        System.out.println("POST CONNECTIONS");
        System.out.println(url);
        return client.post("/connections", url, Boolean.class);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public List<String> getConnections() throws IOException {
        System.out.println("GET CONNECTIONS");
        return client.get("/connections", List.class);
    }

    @Override
    public boolean disconnectPlatform(String url) throws IOException {
        System.out.println("DELETE CONNECTIONS");
        System.out.println(url);
        return client.delete("/connections", url, Boolean.class);
    }

    @Override
    public boolean notifyUpdateContainer(String containerId) throws IOException {
        System.out.println("NOTIFY - CONTAINER");
        System.out.println(containerId);
        return client.post("/containers/notify", containerId, Boolean.class);
    }

    @Override
    public boolean notifyUpdatePlatform(String platformUrl) throws IOException {
        System.out.println("NOTIFY - PLATFORM");
        System.out.println(platformUrl);
        return client.post("/connections/notify", platformUrl, Boolean.class);
    }

}
