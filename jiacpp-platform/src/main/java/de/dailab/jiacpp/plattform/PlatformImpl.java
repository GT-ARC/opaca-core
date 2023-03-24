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
    public void send(String agentId, Message message, boolean forward) throws NoSuchElementException {
        var clients = getClients(null, agentId, null, forward);

        for (ApiProxy client: (Iterable<? extends ApiProxy>) clients::iterator) {
            log.info("Forwarding /send to " + client.baseUrl);
            try {
                client.send(agentId, message, false);
                return;
            } catch (IOException e) {
                log.warning("Failed to forward /send to " + client.baseUrl);
            }
        }
        // TODO should this throw the last IO-Exception if there was any?
        throw new NoSuchElementException(String.format("Not found: agent '%s'", agentId));
    }

    @Override
    public void broadcast(String channel, Message message, boolean forward) {
        var clients = getClients(null, null, null, forward);

        for (ApiProxy client: (Iterable<? extends ApiProxy>) clients::iterator) {
            log.info("Forwarding /broadcast to " + client.baseUrl);
            try {
                client.broadcast(channel, message, false);
            } catch (IOException e) {
                log.warning("Failed to forward /broadcast to " + client.baseUrl);
            }
        }
    }

    @Override
    public JsonNode invoke(String action, Map<String, JsonNode> parameters, boolean forward) throws NoSuchElementException {
        return invoke(null, action, parameters, forward);
    }

    @Override
    public JsonNode invoke(String agentId, String action, Map<String, JsonNode> parameters, boolean forward) throws NoSuchElementException {
        var clients = getClients(null, agentId, action, forward);

        for (ApiProxy client: (Iterable<? extends ApiProxy>) clients::iterator) {
            try {
                return agentId == null
                        ? client.invoke(action, parameters, false)
                        : client.invoke(agentId, action, parameters, false);
            } catch (IOException e) {
                // todo: different warning in case of faulty parameters?
                log.warning(String.format("Failed to invoke action '%s' @ agent '%s' and client '%s'",
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

        // start container... this may raise an Exception, or returns the connectivty info
        var connectivity = containerClient.startContainer(agentContainerId, image);

        // wait until container is up and running...
        var start = System.currentTimeMillis();
        var client = getClient(agentContainerId);
        while (System.currentTimeMillis() < start + config.containerTimeoutSec * 1000) {
            try {
                var container = client.getContainerInfo();
                container.setConnectivity(connectivity);
                runningContainers.put(agentContainerId, container);
                log.info("Container started: " + agentContainerId);
                if (! container.getContainerId().equals(agentContainerId)) {
                    log.warning("Agent Container ID does not match: Expected " +
                            agentContainerId + ", but found " + container.getContainerId());
                }
                notifyConnectedPlatforms();
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
            containerClient.stopContainer(containerId);
            notifyConnectedPlatforms();
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

    @Override
    public boolean notifyUpdateContainer(String containerId) {
        containerId = normalizeUrl(containerId); // remove " - also usable here?
        if (! runningContainers.containsKey(containerId)) {
            var msg = String.format("Container did not exist: %s", containerId);
            log.warning(msg);
            throw new NoSuchElementException(msg);
        }
        try {
            var client = this.getClient(containerId);
            var containerInfo = client.getContainerInfo();
            containerInfo.setConnectivity(runningContainers.get(containerId).getConnectivity());
            runningContainers.put(containerId, containerInfo);
            notifyConnectedPlatforms();
            return true;
        } catch (IOException e) {
            log.warning(String.format("Container did not respond: %s; removing...", containerId));
            runningContainers.remove(containerId);
            return false;
        }
    }

    @Override
    public boolean notifyUpdatePlatform(String platformUrl) {
        platformUrl = normalizeUrl(platformUrl);
        if (platformUrl.equals(config.getOwnBaseUrl())) {
            log.warning("Cannot request update for self.");
            return false;
        }
        if (!connectedPlatforms.containsKey(platformUrl)) {
            var msg = String.format("Platform was not connected: %s", platformUrl);
            log.warning(msg);
            throw new NoSuchElementException(msg);
        }
        try {
            var client = new ApiProxy(platformUrl);
            var platformInfo = client.getPlatformInfo();
            connectedPlatforms.put(platformUrl, platformInfo);
            return true;
        } catch (IOException e) {
            log.warning(String.format("Platform did not respond: %s; removing...", platformUrl));
            connectedPlatforms.remove(platformUrl);
            return false;
        }
    }

    /*
     * HELPER METHODS
     */

    /**
     * Whenever there is a change in this platform's Agent Containers (added, removed, or updated),
     * call the /notify route of all connected Runtime Platforms, so they can pull the updated /info
     *
     * TODO this method is called when something about the containers changes... can we make this
     *      asynchronous (without too much fuzz) so it does not block those other calls?
     */
    private void notifyConnectedPlatforms() {
        for (String platformUrl : connectedPlatforms.keySet()) {
            var client = new ApiProxy(platformUrl);
            try {
                client.notifyUpdatePlatform(config.getOwnBaseUrl());
            } catch (IOException e) {
                log.warning("Failed to forward update to Platform " + platformUrl);
            }
        }
    }

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
     * @param includeConnected Whether to also forward to connected Runtime Platforms
     * @return list of clients to send requests to these valid containers/platforms
     */
    private Stream<ApiProxy> getClients(String containerId, String agentId, String action, boolean includeConnected) {
        // local containers
        var containerClients = runningContainers.values().stream()
                .filter(c -> matches(c, containerId, agentId, action))
                .map(c -> getClient(c.getContainerId()));

        if (!includeConnected) return containerClients;

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
