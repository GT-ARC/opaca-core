package de.dailab.jiacpp.plattform;

import de.dailab.jiacpp.api.RuntimePlatformApi;
import de.dailab.jiacpp.model.*;
import de.dailab.jiacpp.util.RestHelper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Just for testing Agent Container's REST interface, forwarding Calls directly to the Container.
 * Later, the code redirecting to the actual agent containers (after finding the right one) would
 * look similar though.
 */
public class AgentProxy implements RuntimePlatformApi {

    public static final RuntimePlatformApi INSTANCE = new AgentProxy();

    private final RestHelper client = new RestHelper("http://localhost:8082");

    @Override
    public RuntimePlatform getInfo() throws IOException {
        System.out.println("GET INFO");
        AgentContainer container = client.get("/info", AgentContainer.class);
        return new RuntimePlatform(null, List.of(container), null, null);
    }

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
    public void send(String agentId, Message message) throws IOException {
        System.out.println("SEND");
        System.out.println(agentId);
        System.out.println(message);
        client.post(String.format("/send/%s", agentId), message, null);
    }

    @Override
    public void broadcast(String channel, Message message) throws IOException {
        System.out.println("BROADCAST");
        System.out.println(channel);
        System.out.println(message);
        client.post(String.format("/broadcast/%s", channel), message, null);
    }

    @Override
    public Object invoke(String action, Map<String, Object> parameters) throws IOException {
        System.out.println("INVOKE");
        System.out.println(action);
        System.out.println(parameters);
        // TODO object return typo won't work here; rather Map, or "JsonObject"?
        return client.post(String.format("/invoke/%s", action), parameters, Object.class);
    }

    @Override
    public Object invoke(String agentId, String action, Map<String, Object> parameters) throws IOException {
        System.out.println("INVOKE");
        System.out.println(action);
        System.out.println(agentId);
        System.out.println(parameters);
        // TODO object return typo won't work here; rather Map, or "JsonObject"?
        return client.post(String.format("/invoke/%s/%s", action, agentId), parameters, Object.class);
    }

    /*
     * UNSUPPORTED ROUTES
     */

    @Override
    public String addContainer(AgentContainerImage container) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<AgentContainer> getContainers() {
        throw new UnsupportedOperationException();
    }

    @Override
    public AgentContainer getContainer(String containerId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeContainer(String containerId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean connectPlatform(String url) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getConnections() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean disconnectPlatform(String url) {
        throw new UnsupportedOperationException();
    }
}
