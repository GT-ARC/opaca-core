package de.dailab.jiacpp.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import de.dailab.jiacpp.api.RuntimePlatformApi;
import de.dailab.jiacpp.model.*;
import de.dailab.jiacpp.platform.auth.JwtUtil;
import de.dailab.jiacpp.platform.auth.TokenUserDetailsService;
import de.dailab.jiacpp.platform.containerclient.ContainerClient;
import de.dailab.jiacpp.platform.containerclient.DockerClient;
import de.dailab.jiacpp.platform.containerclient.KubernetesClient;
import de.dailab.jiacpp.session.SessionData;
import de.dailab.jiacpp.util.ApiProxy;
import lombok.extern.java.Log;
import de.dailab.jiacpp.util.EventHistory;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * This class provides the actual implementation of the API routes. Might also be split up
 * further, e.g. for agent-forwarding, container-management, and linking to other platforms.
 */
@Log
public class PlatformImpl implements RuntimePlatformApi {

    final SessionData sessionData;
    
    final PlatformConfig config;

    final ContainerClient containerClient;

    final JwtUtil jwtUtil;

    final TokenUserDetailsService userDetailsService;


    /** Currently running Agent Containers, mapping container ID to description */
    private final Map<String, AgentContainer> runningContainers;
    private final Map<String, String> tokens;

    /** Currently connected other Runtime Platforms, mapping URL to description */
    private final Map<String, RuntimePlatform> connectedPlatforms;

    /** Set of remote Runtime Platform URLs with a pending connection request */
    private final Set<String> pendingConnections = new HashSet<>();


    public PlatformImpl(PlatformConfig config, TokenUserDetailsService userDetailsService, JwtUtil jwtUtil, SessionData sessionData) {
        this.config = config;
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
        this.sessionData = sessionData;
        this.runningContainers = sessionData.runningContainers;
        this.tokens = sessionData.tokens;
        this.connectedPlatforms = sessionData.connectedPlatforms;

        if (config.containerEnvironment == PlatformConfig.ContainerEnvironment.DOCKER) {
            log.info("Using Docker on host " + config.remoteDockerHost);
            this.containerClient = new DockerClient();
        } else if (config.containerEnvironment == PlatformConfig.ContainerEnvironment.KUBERNETES) {
            log.info("Using Kubernetes with namespace " + config.kubernetesNamespace);
            this.containerClient = new KubernetesClient();
        } else {
            throw new IllegalArgumentException("Invalid environment specified");
        }

        this.containerClient.initialize(config, sessionData);
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

    @Override
    public List<Event> getHistory() {
        return EventHistory.getInstance().getEvents();
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
    public void send(String agentId, Message message, String containerId, boolean forward) throws IOException, NoSuchElementException {
        var clients = getClients(containerId, agentId, null, forward);

        IOException lastException = null;
        for (ApiProxy client: (Iterable<? extends ApiProxy>) clients::iterator) {
            log.info("Forwarding /send to " + client.baseUrl);
            try {
                client.send(agentId, message, containerId, false);
                return;
            } catch (IOException e) {
                log.warning("Failed to forward /send to " + client.baseUrl + ": " + e);
                lastException = e;
            }
        }
        if (lastException != null) throw lastException;
        throw new NoSuchElementException(String.format("Not found: agent '%s'", agentId));
    }

    @Override
    public void broadcast(String channel, Message message, String containerId, boolean forward) {
        var clients = getClients(containerId, null, null, forward);

        for (ApiProxy client: (Iterable<? extends ApiProxy>) clients::iterator) {
            log.info("Forwarding /broadcast to " + client.baseUrl);
            try {
                client.broadcast(channel, message, containerId, false);
            } catch (IOException e) {
                log.warning("Failed to forward /broadcast to " + client.baseUrl + ": " + e);
            }
        }
    }

    @Override
    public JsonNode invoke(String action, Map<String, JsonNode> parameters, String containerId, boolean forward) throws IOException, NoSuchElementException {
        return invoke(action, parameters, null, containerId, forward);
    }

    @Override
    public JsonNode invoke(String action, Map<String, JsonNode> parameters, String agentId, String containerId, boolean forward) throws IOException, NoSuchElementException {
        var clients = getClients(containerId, agentId, action, forward);

        IOException lastException = null;
        for (ApiProxy client: (Iterable<? extends ApiProxy>) clients::iterator) {
            try {
                return agentId == null
                        ? client.invoke(action, parameters, containerId, false)
                        : client.invoke(action, parameters, agentId, containerId, false);
            } catch (IOException e) {
                log.warning(String.format("Failed to invoke action '%s' @ agent '%s' and client '%s': %s",
                        action, agentId, client.baseUrl, e));
                log.warning("CAUSE " + e.getCause());
                log.warning("MESSAGE " + e.getMessage());
                lastException = e;
            }
        }
        if (lastException != null) throw lastException;
        throw new NoSuchElementException(String.format("Not found: action '%s' @ agent '%s'", action, agentId));
    }

    /*
     * CONTAINERS ROUTES
     */

    @Override
    public String addContainer(AgentContainerImage image) throws IOException {
        String agentContainerId = UUID.randomUUID().toString();
        String token = config.enableAuth ? jwtUtil.generateTokenForAgentContainer(agentContainerId) : "";

        // start container... this may raise an Exception, or returns the connectivity info
        var connectivity = containerClient.startContainer(agentContainerId, token, image);

        // wait until container is up and running...
        var start = System.currentTimeMillis();
        var client = getClient(agentContainerId, token);
        String extraMessage = "";
        while (System.currentTimeMillis() < start + config.containerTimeoutSec * 1000) {
            try {
                var container = client.getContainerInfo();
                container.setConnectivity(connectivity);
                runningContainers.put(agentContainerId, container);
                tokens.put(agentContainerId, token);
                userDetailsService.addUser(agentContainerId, agentContainerId);
                log.info("Container started: " + agentContainerId);
                if (! container.getContainerId().equals(agentContainerId)) {
                    log.warning("Agent Container ID does not match: Expected " +
                            agentContainerId + ", but found " + container.getContainerId());
                }
                notifyConnectedPlatforms();
                return agentContainerId;
            } catch (MismatchedInputException e) {
                extraMessage = "Container returned malformed /info: " + e.getMessage();
                log.warning(extraMessage);
                break;
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
        throw new IOException("Container did not respond with /info in time; stopped. " + extraMessage);
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
            userDetailsService.removeUser(containerId);
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
        url = normalizeString(url);
        checkUrl(url);
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
        url = normalizeString(url);
        checkUrl(url);
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
        containerId = normalizeString(containerId);
        if (! runningContainers.containsKey(containerId)) {
            var msg = String.format("Container did not exist: %s", containerId);
            log.warning(msg);
            throw new NoSuchElementException(msg);
        }
        try {
            var client = this.getClient(containerId, tokens.get(containerId));
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
        platformUrl = normalizeString(platformUrl);
        checkUrl(platformUrl);
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
                .map(c -> getClient(c.getContainerId(), tokens.get(c.getContainerId())));

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
        var url = containerClient.getUrl(containerId);
        return new ApiProxy(url);
    }

    private ApiProxy getClient(String containerId, String token) {
        var url = containerClient.getUrl(containerId);
        return new ApiProxy(url, token);
    }

    private String normalizeString(String string) {
        // string payload may or may not be enclosed in quotes -> normalize
        return string.trim().replaceAll("^\"|\"$", "");
    }

    private void checkUrl(String url) {
        try {
            new URL(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL: " + e.getMessage());
        }
    }

}
