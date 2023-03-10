package de.dailab.jiacpp.plattform;

import com.fasterxml.jackson.databind.JsonNode;
import de.dailab.jiacpp.api.AgentContainerApi;
import de.dailab.jiacpp.api.RuntimePlatformApi;
import de.dailab.jiacpp.model.*;
import de.dailab.jiacpp.plattform.containerclient.ContainerClient;
import de.dailab.jiacpp.plattform.containerclient.DockerClient;
import de.dailab.jiacpp.util.ApiProxy;
import lombok.extern.java.Log;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class provides the actual implementation of the API routes. Might also be split up
 * further, e.g. for agent-forwarding, container-management, and linking to other platforms.
 */
@Log
public class PlatformImpl implements RuntimePlatformApi {

    // TODO make sure agent IDs are globally unique? extend agent-ids with platform-hash or similar?
    //  e.g. optionally allow "agentId@containerId" to be passed in place of agentId for all routes?

    final PlatformConfig config;

    final ContainerClient containerClient;

    /** Currently running Agent Containers, mapping container ID to description */
    private final Map<String, AgentContainer> runningContainers = new HashMap<>();

    /** Currently connected other Runtime Platforms, mapping URL to description */
    private final Map<String, RuntimePlatform> connectedPlatforms = new HashMap<>();

    /** Set of remote Runtime Platform URLs with a pending connection request */
    private final Set<String> pendingConnections = new HashSet<>();


    public PlatformImpl(PlatformConfig config) {
        this.config = config;
        this.containerClient = new DockerClient();
        this.containerClient.initialize(config);
    }

    @Override
    public RuntimePlatform getPlatformInfo() throws IOException {
        return new RuntimePlatform(
                config.getOwnBaseUrl(),
                List.copyOf(runningContainers.values()),
                List.of(), // TODO "provides" pf platform?
                List.copyOf(connectedPlatforms.keySet())
        );
    }

    /*
     * AGENTS ROUTES
     */

    @Override
    public List<AgentDescription> getAgents() throws IOException {
        return runningContainers.values().stream()
                .flatMap(c -> c.getAgents().stream())
                .collect(Collectors.toList());
    }

    @Override
    public AgentDescription getAgent(String agentId) throws IOException {
        return runningContainers.values().stream()
                .flatMap(c -> c.getAgents().stream())
                .filter(a -> a.getAgentId().equals(agentId))
                .findAny().orElse(null);
    }

    @Override
    public void send(String agentId, Message message) throws NoSuchElementException {
        var clients = getClients(null, agentId, null);
        if (clients.isEmpty()) {
            throw new NoSuchElementException(String.format("Not found: agent '%s'", agentId));
        }
        for (ApiProxy client: clients) {
            log.info("Forwarding /send to " + client.baseUrl);
            try {
                client.send(agentId, message);
            } catch (IOException e) {
                log.warning("Failed to forward /send to " + client.baseUrl);
            }
        }
    }

    @Override
    public void broadcast(String channel, Message message) throws IOException {
        for (AgentContainer container : runningContainers.values()) {
            getClient(container).broadcast(channel, message);
        }
        // TODO how to handle IO Exception forwarding to a single container/platform, if broadcast to all others worked?
        // TODO broadcast to other platforms... how to prevent infinite loops? optional flag parameter?
    }

    @Override
    public JsonNode invoke(String action, Map<String, JsonNode> parameters) throws IOException {
        return invoke(null, action, parameters);
    }

    @Override
    public JsonNode invoke(String agentId, String action, Map<String, JsonNode> parameters) throws NoSuchElementException {
        var clients = getClients(null, agentId, action);

        for (ApiProxy client: clients) {
            try {
                return agentId == null
                        ? client.invoke(action, parameters)
                        : client.invoke(agentId, action, parameters);
            } catch (IOException e) {
                // todo: different warning in case of faulty parameters?
                log.warning(String.format("Failed to invoke action %s @ agent %s and client %s",
                        action, agentId, client.baseUrl));
            }
        }

        // iterated over all clients, no valid client found
        throw new NoSuchElementException(String.format("Not found: action '%s' @ agent '%s'", action, agentId));
    }

    /*
     * CONTAINERS ROUTES
     */

    @Override
    public String addContainer(AgentContainerImage image) throws IOException {
        String agentContainerId = UUID.randomUUID().toString();

        // start container... this may raise an Exception, but otherwise has no result
        containerClient.startContainer(agentContainerId, image.getImageName());

        // wait until container is up and running...
        var start = System.currentTimeMillis();
        var client = getClient(agentContainerId);
        while (System.currentTimeMillis() < start + config.containerTimeoutSec * 1000) {
            try {
                var container = client.getContainerInfo();
                runningContainers.put(agentContainerId, container);
                log.info("Container started: " + agentContainerId);
                if (! container.getContainerId().equals(agentContainerId)) {
                    log.warning("Agent Container ID does not match: Expected " +
                            agentContainerId + ", but found " + container.getContainerId());
                }
                return agentContainerId;
            } catch (IOException e) {
                // this is normal... waiting for container to start and provide services
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.severe(e.getMessage());
            }
        }

        // if we reach this point, container did not start in time or does not provide /info route
        try {
            containerClient.stopContainer(agentContainerId);
        } catch (Exception e) {
            log.warning("Failed to stop container: " + e.getMessage());
        }
        throw new IOException("Container did not respond to API calls in time; stopped.");
    }

    @Override
    public List<AgentContainer> getContainers() throws IOException {
        return List.copyOf(runningContainers.values());
    }

    @Override
    public AgentContainer getContainer(String containerId) throws IOException {
        return runningContainers.get(containerId);
    }

    @Override
    public boolean removeContainer(String containerId) throws IOException {
        AgentContainer container = runningContainers.get(containerId);
        if (container != null) {
            containerClient.stopContainer(containerId);
            runningContainers.remove(containerId);
            return true;
        }
        return false;
    }

    /*
     * CONNECTIONS ROUTES
     */

    @Override
    public boolean connectPlatform(String url) throws IOException {
        url = normalizeUrl(url);
        if (url.equals(config.getOwnBaseUrl()) || connectedPlatforms.containsKey(url)) {
            return false;
        } else if (pendingConnections.contains(url)) {
            // callback from remote platform following our own request for connection
            return true;
        } else {
            try {
                pendingConnections.add(url);
                var client = new ApiProxy(url);
                var res = client.connectPlatform(config.getOwnBaseUrl());
                if (res) {
                    var info = client.getPlatformInfo();
                    connectedPlatforms.put(url, info);
                }
                return true;
            } finally {
                // also remove from pending in case client.post fails
                pendingConnections.remove(url);
            }
        }
    }

    @Override
    public List<String> getConnections() {
        return List.copyOf(connectedPlatforms.keySet());
    }

    @Override
    public boolean disconnectPlatform(String url) throws IOException {
        url = normalizeUrl(url);
        if (connectedPlatforms.remove(url) != null) {
            var client = new ApiProxy(url);
            var res = client.disconnectPlatform(config.getOwnBaseUrl());
            log.info(String.format("Disconnected from %s: %s", url, res));
            // TODO how to handle IO Exception here? other platform still there but refuses to disconnect?
            return true;
        }
        return false;
    }

    /*
     * HELPER METHODS
     */


    /**
     * Get client for send or invoke to specific agent, in a specific container, for a specific action.
     * All those parameters are optional (e.g. for "send" or "invoke" without agentId), but obviously
     * _some_ should be set, otherwise it does not make much sense to call the method.
     */
    private ApiProxy getClient(String containerId, String agentId, String action) throws IOException {
        // TODO for /invoke: also check that action parameters match, or just the name?
        // check own containers
        var container = runningContainers.values().stream()
                .filter(c -> matches(c, containerId, agentId, action))
                .findFirst();
        if (container.isPresent()) {
            return getClient(container.get());
        }
        // check containers on connected platforms
        var platform = connectedPlatforms.values().stream()
                .filter(p -> p.getContainers().stream().anyMatch(c -> matches(c, containerId, agentId, action)))
                .findFirst();
        if (platform.isPresent()) {
            return new ApiProxy(platform.get().getBaseUrl());
        }
        return null;
    }

    /**
     * get a list of clients for all containers/platforms that fulfill the given agent/action requirements.
     *
     * @param containerId container on which should be searched for valid agents/actions
     * @param agentId ID of the agent on which the action should be invoked or to which a message should be sent
     * @param action name of the action that should be invoked
     * @return list of clients to send requests to these valid containers/platforms
     */
    private List<ApiProxy> getClients(String containerId, String agentId, String action) {
        // local containers
        var clients = runningContainers.values().stream()
                .filter(c -> matches(c, containerId, agentId, action))
                .map(c -> getClient(c.getContainerId()))
                .collect(Collectors.toList());

        // remote platforms
        clients.addAll(connectedPlatforms.values().stream()
                .filter(p -> p.getContainers().stream().anyMatch(c -> matches(c, containerId, agentId, action)))
                .map(p -> new ApiProxy(p.getBaseUrl()))
                .collect(Collectors.toList()));

        return clients;
    }

    /**
     * Check if Container ID matches and has matching agent and/or action.
     */
    private boolean matches(AgentContainer container, String containerId, String agentId, String action) {
        return (containerId == null || container.getContainerId().equals(containerId)) &&
                container.getAgents().stream()
                        .anyMatch(a -> (agentId == null || a.getAgentId().equals(agentId))
                                && (action == null || a.getActions().stream()
                                .anyMatch(x -> x.getName().equals(action))));
    }

    private ApiProxy getClient(AgentContainer container) {
        return getClient(container.getContainerId());
    }

    private ApiProxy getClient(String containerId) {
        var ip = containerClient.getIP(containerId);
        return new ApiProxy(String.format("http://%s:%s", ip, AgentContainerApi.DEFAULT_PORT));
    }

    private String normalizeUrl(String url) {
        // string payload may or may not be enclosed in quotes -> normalize
        return url.trim().replaceAll("^\"|\"$", "");
    }

}
