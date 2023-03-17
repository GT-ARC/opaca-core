package de.dailab.jiacpp.plattform;

import com.fasterxml.jackson.databind.JsonNode;
import de.dailab.jiacpp.api.AgentContainerApi;
import de.dailab.jiacpp.api.RuntimePlatformApi;
import de.dailab.jiacpp.model.*;
import de.dailab.jiacpp.plattform.containerclient.ContainerClient;
import de.dailab.jiacpp.plattform.containerclient.DockerClient;
import de.dailab.jiacpp.util.ApiProxy;
import lombok.extern.java.Log;

import javax.validation.metadata.ContainerDescriptor;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class provides the actual implementation of the API routes. Might also be split up
 * further, e.g. for agent-forwarding, container-management, and linking to other platforms.
 */
@Log
public class PlatformImpl implements RuntimePlatformApi {

    // TODO make sure agent IDs are globally unique? extend agent-ids with platform-hash or similar?
    //  e.g. optionally allow "agentId@containerId" to be passed in place of agentId for all routes? (Issue #10)

    final PlatformConfig config;

    final ContainerClient containerClient;

    /** Currently running Agent Containers, mapping container ID to description */
    private final Map<String, AgentContainer> runningContainers = new HashMap<>();

    /** Currently connected other Runtime Platforms, mapping URL to description */
    private final Map<String, RuntimePlatform> connectedPlatforms = new HashMap<>();

    /** Set of remote Runtime Platform URLs with a pending connection request */
    private final Set<String> pendingConnections = new HashSet<>();

    /** Port mappings of currently running containers */
    private final Map<String, Map<Integer, Integer>> portMappings = new HashMap<>();
    // TODO remember to also remove those after failed to fetch /info in notify/update

    public PlatformImpl(PlatformConfig config) {
        this.config = config;
        this.containerClient = new DockerClient();
        this.containerClient.initialize(config);
        // TODO add list of known used ports to config (e.g. the port of the RP itself, or others)
    }

    @Override
    public RuntimePlatform getPlatformInfo() {
        return new RuntimePlatform(
                config.getOwnBaseUrl(),
                List.copyOf(runningContainers.values()),
                List.of(), // TODO "provides" pf platform? read from config? issue #42
                List.copyOf(connectedPlatforms.keySet())
        );
    }

    /*
     * AGENTS ROUTES
     */

    @Override
    public List<AgentDescription> getAgents() {
        return runningContainers.values().stream()
                .flatMap(c -> c.getAgents().stream())
                .collect(Collectors.toList());
    }

    @Override
    public AgentDescription getAgent(String agentId) {
        return runningContainers.values().stream()
                .flatMap(c -> c.getAgents().stream())
                .filter(a -> a.getAgentId().equals(agentId))
                .findAny().orElse(null);
    }

    @Override
    public void send(String agentId, Message message) throws NoSuchElementException {
        var clients = getClients(null, agentId, null);

        for (ApiProxy client: (Iterable<? extends ApiProxy>) clients::iterator) {
            log.info("Forwarding /send to " + client.baseUrl);
            try {
                client.send(agentId, message);
                return;
            } catch (IOException e) {
                log.warning("Failed to forward /send to " + client.baseUrl);
            }
        }
        // TODO should this throw the last IO-Exception if there was any?
        throw new NoSuchElementException(String.format("Not found: agent '%s'", agentId));
    }

    @Override
    public void broadcast(String channel, Message message) {
        for (AgentContainer container : runningContainers.values()) {
            try {
                getClient(container).broadcast(channel, message);
            } catch (IOException e) {
                log.warning("Failed to forward /broadcast to Container " + container.getContainerId());
            }
        }
        // TODO should this raise an IOException if there was one for any of the clients?
        //  (after broadcasting to all other reachable clients!)
        // TODO broadcast to other platforms... how to prevent infinite loops? optional flag parameter?
    }

    @Override
    public JsonNode invoke(String action, Map<String, JsonNode> parameters) throws NoSuchElementException {
        return invoke(null, action, parameters);
    }

    @Override
    public JsonNode invoke(String agentId, String action, Map<String, JsonNode> parameters) throws NoSuchElementException {
        var clients = getClients(null, agentId, action);

        for (ApiProxy client: (Iterable<? extends ApiProxy>) clients::iterator) {
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
        // TODO should this throw the last IO-Exception if there was any?
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
        var portMap = containerClient.startContainer(agentContainerId, image.getImageName());
        portMappings.put(agentContainerId, portMap);

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
    public List<AgentContainer> getContainers() {
        return List.copyOf(runningContainers.values());
    }

    @Override
    public AgentContainer getContainer(String containerId) {
        return runningContainers.get(containerId);
    }

    @Override
    public boolean removeContainer(String containerId) throws IOException {
        AgentContainer container = runningContainers.get(containerId);
        if (container != null) {
            runningContainers.remove(containerId);
            portMappings.remove(containerId);
            containerClient.stopContainer(containerId);
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
    private Stream<ApiProxy> getClients(String containerId, String agentId, String action) {
        // local containers
        var containerClients = runningContainers.values().stream()
                .filter(c -> matches(c, containerId, agentId, action))
                .map(c -> getClient(c.getContainerId()));

        // remote platforms
        var platformClients = connectedPlatforms.values().stream()
                .filter(p -> p.getContainers().stream().anyMatch(c -> matches(c, containerId, agentId, action)))
                .map(p -> new ApiProxy(p.getBaseUrl()));

        return Stream.concat(containerClients, platformClients);
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

    // TODO remember to also call this after calling /info in notify/update
    private AgentContainer withPortMappings(AgentContainer info) {
        // TODO add port mappings to container
        //  - iterate port in port mappings
        //  - if api port, set it
        //  - else get matching port descriptor from image
        //  - add port and set in container's description
        //  - what about publicUrl? or wrap all that into another nested class/object and have addContainer return it?
        return info;
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
