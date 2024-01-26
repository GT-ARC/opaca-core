package de.gtarc.opaca.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import de.gtarc.opaca.api.RuntimePlatformApi;
import de.gtarc.opaca.platform.auth.JwtUtil;
import de.gtarc.opaca.platform.user.TokenUserDetailsService;
import de.gtarc.opaca.platform.containerclient.ContainerClient;
import de.gtarc.opaca.platform.containerclient.DockerClient;
import de.gtarc.opaca.platform.containerclient.KubernetesClient;
import de.gtarc.opaca.platform.session.SessionData;
import de.gtarc.opaca.model.*;
import de.gtarc.opaca.util.ApiProxy;
import de.gtarc.opaca.util.ArgumentValidator;
import com.networknt.schema.JsonSchema;
import lombok.extern.java.Log;
import de.gtarc.opaca.util.EventHistory;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;


/**
 * This class provides the actual implementation of the API routes. Might also be split up
 * further, e.g. for agent-forwarding, container-management, and linking to other platforms.
 */
@Log
@Component
public class PlatformImpl implements RuntimePlatformApi {

    @Autowired
    private SessionData sessionData;

    @Autowired
    private PlatformConfig config;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TokenUserDetailsService userDetailsService;


    private ContainerClient containerClient;


    /** Currently running Agent Containers, mapping container ID to description */
    private Map<String, AgentContainer> runningContainers;
    private Map<String, PostAgentContainer> startedContainers;
    private Map<String, String> tokens;

    /** Currently connected other Runtime Platforms, mapping URL to description */
    private Map<String, RuntimePlatform> connectedPlatforms;

    /** Set of remote Runtime Platform URLs with a pending connection request */
    private final Set<String> pendingConnections = new HashSet<>();


    @PostConstruct
    public void initialize() {
        this.runningContainers = sessionData.runningContainers;
        this.startedContainers = sessionData.startContainerRequests;
        this.tokens = sessionData.tokens;
        this.connectedPlatforms = sessionData.connectedPlatforms;

        // initialize container client based on environment
        if (config.containerEnvironment == PostAgentContainer.ContainerEnvironment.DOCKER) {
            log.info("Using Docker on host " + config.remoteDockerHost);
            this.containerClient = new DockerClient();
        } else if (config.containerEnvironment == PostAgentContainer.ContainerEnvironment.KUBERNETES) {
            log.info("Using Kubernetes with namespace " + config.kubernetesNamespace);
            this.containerClient = new KubernetesClient();
        } else {
            throw new IllegalArgumentException("Invalid environment specified");
        }
        // test resolving own base URL and print result
        log.info("Own Base URL: " + config.getOwnBaseUrl());

        this.containerClient.initialize(config, sessionData);
        this.containerClient.testConnectivity();
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
    public String login(Login loginParams) {
        return jwtUtil.generateTokenForUser(loginParams.getUsername(), loginParams.getPassword());
    }
    

    @Override
    public void send(String agentId, Message message, String containerId, boolean forward) throws IOException, NoSuchElementException {
        var clients = getClients(containerId, agentId, null, null, forward);

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
        var clients = getClients(containerId, null, null, null, forward);

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
    public JsonNode invoke(String action, Map<String, JsonNode> parameters, int timeout, String containerId, boolean forward) throws IOException, NoSuchElementException {
        return invoke(action, parameters, null, timeout, containerId, forward);
    }

    @Override
    public JsonNode invoke(String action, Map<String, JsonNode> parameters, String agentId, int timeout, String containerId, boolean forward) throws IOException, NoSuchElementException {
        var clients = getClients(containerId, agentId, action, null, forward);

        IOException lastException = null;
        for (ApiProxy client: (Iterable<? extends ApiProxy>) clients::iterator) {
            try {
                return agentId == null
                        ? client.invoke(action, parameters, timeout, containerId, false)
                        : client.invoke(action, parameters, agentId, timeout, containerId, false);
            } catch (IOException e) {
                log.warning(String.format("Failed to invoke action '%s' @ agent '%s' and client '%s': %s",
                        action, agentId, client.baseUrl, e));
                lastException = e;
            }
        }
        if (lastException != null) throw lastException;
        throw new NoSuchElementException(String.format("Not found: action '%s' @ agent '%s'", action, agentId));
    }

    @Override
    public ResponseEntity<StreamingResponseBody> getStream(String stream, String containerId, boolean forward) throws IOException, NoSuchElementException {
        return getStream(stream, null, containerId, forward);
    }

    @Override
    public ResponseEntity<StreamingResponseBody> getStream(String stream, String agentId, String containerId, boolean forward) throws IOException {
        var clients = getClients(containerId, agentId, null, stream, forward);
        
        IOException lastException = null;
        for (ApiProxy client: (Iterable<? extends ApiProxy>) clients::iterator) {
            try {
                return agentId == null
                        ? client.getStream(stream, containerId, false)
                        : client.getStream(stream, agentId, containerId, false);
            } catch (IOException e) {
                log.warning(String.format("Failed to get stream '%s' @ agent '%s' and client '%s': %s",
                        stream, agentId, client.baseUrl, e));
                lastException = e;
            }
        }
        if (lastException != null) throw lastException;
        throw new NoSuchElementException(String.format("Not found: stream '%s' @ agent '%s'", stream, agentId));
    }
    
    /*
     * CONTAINERS ROUTES
     */

 @Override
    public void postStream(String stream, byte[] inputStream, String containerId, boolean forward) throws IOException {
        postStream(stream, inputStream, null, containerId, forward);
    }

    @Override
    public void postStream(String stream, byte[] inputStream, String agentId, String containerId, boolean forward) throws IOException {
        var clients = getClients(containerId, agentId, null, stream, forward);
        
        IOException lastException = null;
        for (ApiProxy client: (Iterable<? extends ApiProxy>) clients::iterator) {
            try {
                if (agentId == null) {
                    client.postStream(stream, inputStream, containerId, false);
                } else {
                    client.postStream(stream, inputStream, agentId, containerId, false);
                }
                return;
            } catch (IOException e) {
                log.warning(String.format("Failed to post stream '%s' @ agent '%s' and client '%s': %s",
                        stream, agentId, client.baseUrl, e));
                lastException = e;
            }
        }
        if (lastException != null) throw lastException;
        throw new NoSuchElementException(String.format("Not found: stream '%s' @ agent '%s'", stream, agentId));
    }

    @Override
    public String addContainer(PostAgentContainer postContainer) throws IOException {
        checkConfig(postContainer);
        String agentContainerId = UUID.randomUUID().toString();
        String token = "";
        String owner = "";
        if (config.enableAuth) {
            token = jwtUtil.generateTokenForAgentContainer(agentContainerId);
            owner = userDetailsService.getTokenUser(jwtUtil.getCurrentRequestUser()).getUsername();
        }

        // start container... this may raise an Exception, or returns the connectivity info
        var connectivity = containerClient.startContainer(agentContainerId, token, owner, postContainer);

        // wait until container is up and running...
        var start = System.currentTimeMillis();
        var client = getClient(agentContainerId, token);
        String extraMessage = "";
        while (System.currentTimeMillis() < start + config.containerTimeoutSec * 1000L) {
            try {
                var container = client.getContainerInfo();
                container.setConnectivity(connectivity);
                runningContainers.put(agentContainerId, container);
                startedContainers.put(agentContainerId, postContainer);
                tokens.put(agentContainerId, token);
                container.setOwner(owner);
                userDetailsService.createUser(agentContainerId, agentContainerId,
                        config.enableAuth ? userDetailsService.getUserRole(owner) : Role.GUEST,
                        config.enableAuth ? userDetailsService.getUserPrivileges(owner) : null);
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
        if (config.enableAuth) {
            // If request user does not have user profile, throw FORBIDDEN exception
            UserDetails details = userDetailsService.loadUserByUsername(jwtUtil.getCurrentRequestUser());
            if (details == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            // TODO Not sure if this is the right place to handle custom Http responses
            //  Might need to implement more custom error handling
            // If user is neither admin nor owner of container, throw FORBIDDEN exception
            if (details.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals(Role.ADMIN.role())) &&
                    !details.getUsername().equals(container.getOwner())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        }
        if (container == null) return false;
        runningContainers.remove(containerId);
        startedContainers.remove(containerId);
        userDetailsService.removeUser(containerId);
        containerClient.stopContainer(containerId);
        notifyConnectedPlatforms();
        return true;
    }

    /*
     * CONNECTIONS ROUTES
     */

    @Override
    public boolean connectPlatform(LoginConnection loginConnection) throws IOException {
        String url = normalizeString(loginConnection.getUrl());
        checkUrl(url);
        if (url.equals(config.getOwnBaseUrl()) || connectedPlatforms.containsKey(url)) {
            return false;
        } else if (pendingConnections.contains(url)) {
            // callback from remote platform following our own request for connection
            return true;
        } else {
            if (loginConnection.getUsername() != null) {
                // with auth, unidirectional
                var token = new ApiProxy(url).login(new Login(loginConnection.getUsername(), loginConnection.getPassword()));
                var info = new ApiProxy(url, token).getPlatformInfo();
                connectedPlatforms.put(url, info);
                tokens.put(url, token);
            } else {
                // without auth, bidirectional
                try {
                    var info = new ApiProxy(url).getPlatformInfo();
                    url = info.getBaseUrl();
                    pendingConnections.add(url);
                    if (new ApiProxy(url).connectPlatform(new LoginConnection(null, null, config.getOwnBaseUrl()))) {
                        connectedPlatforms.put(url, info);
                    }
                } finally {
                    // also remove from pending in case client.post fails
                    pendingConnections.remove(url);
                }
            }
            return true;
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
            if (tokens.containsKey(url)) {
                tokens.remove(url);
            } else {
                new ApiProxy(url).disconnectPlatform(config.getOwnBaseUrl());
            }
            log.info(String.format("Disconnected from %s", url));
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
        // TODO with connect not being bidirectional, this does not really make sense anymore
        //  adapt to possible new /subscribe route, or change entirely?
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
    private Stream<ApiProxy> getClients(String containerId, String agentId, String action, String stream, boolean includeConnected) {
        // local containers
        var containerClients = runningContainers.values().stream()
                .filter(c -> matches(c, containerId, agentId, action, stream))
                .map(c -> getClient(c.getContainerId(), tokens.get(c.getContainerId())));

        if (!includeConnected) return containerClients;

        // remote platforms
        var platformClients = connectedPlatforms.entrySet().stream()
            .filter(entry -> entry.getValue().getContainers().stream().anyMatch(c -> matches(c, containerId, agentId, action, stream)))
            .map(entry -> {return new ApiProxy(entry.getKey(), tokens.get(entry.getKey()));});

        return Stream.concat(containerClients, platformClients);
    }
    /**
     * Check if Container ID matches and has matching agent and/or action.
     */
    private boolean matches(AgentContainer container, String containerId, String agentId, String action, String stream) {
        return (containerId == null || container.getContainerId().equals(containerId)) &&
                container.getAgents().stream()
                        .anyMatch(a -> (agentId == null || a.getAgentId().equals(agentId))
                                && (action == null || a.getActions().stream().anyMatch(x -> x.getName().equals(action)))
                                && (stream == null || a.getStreams().stream().anyMatch(x -> x.getName().equals(stream)))
                        );
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

    private void checkConfig(PostAgentContainer request) {
        if (request.getClientConfig() != null && request.getClientConfig().getType() != config.containerEnvironment) {
            throw new IllegalArgumentException(String.format("Client Config %s does not match Container Environment %s",
                    request.getClientConfig().getType(), config.containerEnvironment));
        }
    }

    /**
     * Creates a random String of length 24 containing upper and lower case characters as well as number
     */
    private String generateRandomPwd() {
        return RandomStringUtils.random(24, true, true);
    }

    private boolean validateArguments(
            Map<String, JsonSchema> definitions,
            Map<String, Parameter> parameters,
            Map<String, JsonNode> arguments) {
        var validator = new ArgumentValidator(definitions, parameters);

        // test call
        validator.isArgsValid(arguments);

        return true;
    }

}
