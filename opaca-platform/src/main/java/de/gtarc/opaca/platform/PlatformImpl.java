package de.gtarc.opaca.platform;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import de.gtarc.opaca.api.RuntimePlatformApi;
import de.gtarc.opaca.platform.auth.JwtUtil;
import de.gtarc.opaca.platform.user.TokenUserDetailsService;
import de.gtarc.opaca.platform.containerclient.ContainerClient;
import de.gtarc.opaca.platform.containerclient.DockerClient;
import de.gtarc.opaca.platform.containerclient.KubernetesClient;
import de.gtarc.opaca.platform.session.SessionData;
import de.gtarc.opaca.model.*;
import de.gtarc.opaca.util.ApiProxy;
import lombok.extern.java.Log;
import de.gtarc.opaca.util.EventHistory;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.web.server.ResponseStatusException;


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

    /** Map of validators for validating action argument types for each container */
    private final Map<String, ArgumentValidator> validators = new HashMap<>();


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

        for (var containerId : runningContainers.keySet()) {
            var image = runningContainers.get(containerId).getImage();
            validators.put(containerId, new ArgumentValidator(image));
        }
    }

    @Override
    public RuntimePlatform getPlatformInfo() {
        return new RuntimePlatform(
                config.getOwnBaseUrl(),
                List.copyOf(runningContainers.values()),
                List.of(), // TODO "provides" of platform? read from config? issue #42
                List.copyOf(connectedPlatforms.keySet())
        );
    }

    @Override
    public Map<String, ?> getPlatformConfig() throws IOException {
        return config.toMap();
    }

    @Override
    public List<Event> getHistory() {
        return EventHistory.getInstance().getEvents();
    }

    @Override
    public String login(Login loginParams) {
        return jwtUtil.generateTokenForUser(loginParams.getUsername(), loginParams.getPassword());
    }

    @Override
    public String renewToken() {
        // if auth is disabled, this produces "Username not found" and thus 403, which is a bit weird but okay...
        String owner = userDetailsService.getUser(jwtUtil.getCurrentRequestUser()).getUsername();
        return jwtUtil.generateTokenForAgentContainer(owner);
    }

    /*
     * AGENTS ROUTES
     */

    @Override
    public List<AgentDescription> getAgents() {
        return streamAgents(false).collect(Collectors.toList());
    }

    @Override
    public List<AgentDescription> getAllAgents() throws IOException {
        return streamAgents(true).collect(Collectors.toList());
    }

    @Override
    public AgentDescription getAgent(String agentId) {
        return streamAgents(true)
                .filter(a -> a.getAgentId().equals(agentId))
                .findAny().orElse(null);
    }

    @Override
    public void send(String agentId, Message message, String containerId, boolean forward) throws IOException, NoSuchElementException {
        var clients = getClients(containerId, agentId, null, null, null, forward);

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
        var clients = getClients(containerId, null, null, null, null, forward);

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
    public JsonNode invoke(String action, Map<String, JsonNode> parameters, String agentId, int timeout, String containerId, boolean forward) throws IOException, NoSuchElementException {
        var clients = getClients(containerId, agentId, action, parameters, null, forward);

        IOException lastException = null;
        for (ApiProxy client: (Iterable<? extends ApiProxy>) clients::iterator) {
            try {
                return client.invoke(action, parameters, agentId, timeout, containerId, false);
            } catch (IOException e) {
                log.warning(String.format("Failed to invoke action '%s' @ agent '%s' and client '%s': %s",
                        action, agentId, client.baseUrl, e));
                lastException = e;
            }
        }
        if (lastException != null) throw lastException;
        throw new NoSuchElementException(String.format("Not found: action '%s' @ agent '%s', or the given parameters are invalid.", action, agentId));
    }

    @Override
    public InputStream getStream(String stream, String agentId, String containerId, boolean forward) throws IOException {
        var clients = getClients(containerId, agentId, null, null, stream, forward);

        IOException lastException = null;
        for (ApiProxy client: (Iterable<? extends ApiProxy>) clients::iterator) {
            try {
                return client.getStream(stream, agentId, containerId, false);
            } catch (IOException e) {
                log.warning(String.format("Failed to get stream '%s' @ agent '%s' and client '%s': %s",
                        stream, agentId, client.baseUrl, e));
                lastException = e;
            }
        }
        if (lastException != null) throw lastException;
        throw new NoSuchElementException(String.format("Not found: stream '%s' @ agent '%s'", stream, agentId));
    }

    @Override
    public void postStream(String stream, byte[] inputStream, String agentId, String containerId, boolean forward) throws IOException {
        var clients = getClients(containerId, agentId, null, null, stream, forward);
        
        IOException lastException = null;
        for (ApiProxy client: (Iterable<? extends ApiProxy>) clients::iterator) {
            try {
                client.postStream(stream, inputStream, agentId, containerId, false);
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
    
    /*
     * CONTAINERS ROUTES
     */

    @Override
    public String addContainer(PostAgentContainer postContainer) throws IOException {
        checkConfig(postContainer);
        String agentContainerId = UUID.randomUUID().toString();
        String token = "";
        String owner = "";
        if (config.enableAuth) {
            token = jwtUtil.generateTokenForAgentContainer(agentContainerId);
            owner = userDetailsService.getUser(jwtUtil.getCurrentRequestUser()).getUsername();
        }

        // start container... this may raise an Exception, or returns the connectivity info
        var connectivity = containerClient.startContainer(agentContainerId, token, owner, postContainer);

        // wait until container is up and running...
        var start = System.currentTimeMillis();
        var client = getClient(agentContainerId, token);
        String errorMessage = "Container did not respond with /info in time.";
        while (System.currentTimeMillis() < start + config.containerTimeoutSec * 1000L) {
            try {
                var container = client.getContainerInfo();
                container.setConnectivity(connectivity);
                runningContainers.put(agentContainerId, container);
                startedContainers.put(agentContainerId, postContainer);
                tokens.put(agentContainerId, token);
                validators.put(agentContainerId, new ArgumentValidator(container.getImage()));
                container.setOwner(owner);
                userDetailsService.createUser(agentContainerId, generateRandomPwd(),
                        config.enableAuth ? userDetailsService.getUserRole(owner) : Role.GUEST,
                        config.enableAuth ? userDetailsService.getUserPrivileges(owner) : null);
                log.info("Container started: " + agentContainerId);
                if (! container.getContainerId().equals(agentContainerId)) {
                    log.warning("Agent Container ID does not match: Expected " +
                            agentContainerId + ", but found " + container.getContainerId());
                }
                notifyConnectedPlatforms();
                return agentContainerId;
            } catch (JsonMappingException e) {
                errorMessage = "Container returned malformed /info: " + e.getMessage();
                break;
            } catch (IOException e) {
                // this is normal... waiting for container to start and provide services
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.severe(e.getMessage());
            }
            if (! containerClient.isContainerAlive(agentContainerId)) {
                errorMessage = "Container failed to start.";
                break;
            }
        }

        // if we reach this point, container did not start in time or does not provide /info route
        log.warning("Stopping Container. " + errorMessage);
        try {
            containerClient.stopContainer(agentContainerId);
        } catch (Exception e) {
            log.warning("Failed to stop container: " + e.getMessage());
        }
        throw new IOException(errorMessage);
    }

    @Override
    public String updateContainer(PostAgentContainer container) throws IOException {
        var matchingContainers = runningContainers.values().stream()
                .filter(c -> c.getImage().getImageName().equals(container.getImage().getImageName()))
                .toList();
        switch (matchingContainers.size()) {
            case 1: {
                var oldContainer = matchingContainers.get(0);
                removeContainer(oldContainer.getContainerId());
                return addContainer(container);
            }
            case 0:
                throw new IllegalArgumentException("No matching container is currently running; please use POST instead.");
            default:
                throw new IllegalArgumentException("More than one matching container is currently running; please DELETE manually, then POST.");
        }
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
        validators.remove(containerId);
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
                var token = getPlatformClient(url).login(new Login(loginConnection.getUsername(), loginConnection.getPassword()));
                var info = getPlatformClient(url, token).getPlatformInfo();
                connectedPlatforms.put(url, info);
                tokens.put(url, token);
            } else {
                // without auth, bidirectional
                try {
                    var info = getPlatformClient(url).getPlatformInfo();
                    url = info.getBaseUrl();
                    pendingConnections.add(url);
                    if (getPlatformClient(url).connectPlatform(new LoginConnection(null, null, config.getOwnBaseUrl()))) {
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
                getPlatformClient(url).disconnectPlatform(config.getOwnBaseUrl());
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
            validators.put(containerId, new ArgumentValidator(containerInfo.getImage()));
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
            var client = getPlatformClient(platformUrl);
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
            var client = getPlatformClient(platformUrl);
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
    private Stream<ApiProxy> getClients(String containerId, String agentId, String action, Map<String, JsonNode> parameters, String stream, boolean includeConnected) {
        // local containers
        var containerClients = runningContainers.values().stream()
                .filter(c -> matches(c, containerId, agentId, action, parameters, stream))
                .map(c -> getClient(c.getContainerId(), tokens.get(c.getContainerId())));

        if (!includeConnected) return containerClients;

        // remote platforms
        var platformClients = connectedPlatforms.entrySet().stream()
            .filter(entry -> entry.getValue().getContainers().stream().anyMatch(c -> matches(c, containerId, agentId, action, parameters, stream)))
            .map(entry -> getPlatformClient(entry.getKey(), tokens.get(entry.getKey())));

        return Stream.concat(containerClients, platformClients);
    }

    /**
     * Get Stream of all Agents on this platform or on this and connected platforms.
     */
    private Stream<AgentDescription> streamAgents(boolean includeConnected) {
        var containers = includeConnected ? Stream.concat(
                runningContainers.values().stream(),
                connectedPlatforms.values().stream().flatMap(rp -> rp.getContainers().stream())
            ) : runningContainers.values().stream();
        return containers.flatMap(c -> c.getAgents().stream());
    }

    /**
     * Check if Container ID matches and has matching agent and/or action.
     */
    private boolean matches(AgentContainer container, String containerId, String agentId, String action, Map<String, JsonNode> arguments, String stream) {
        var validator = validators.containsKey(container.getContainerId())
                ? validators.get(container.getContainerId()) // own container
                : new ArgumentValidator(container.getImage()); // connected rp container
        return (containerId == null || container.getContainerId().equals(containerId)) &&
                container.getAgents().stream()
                        .anyMatch(a -> (agentId == null || a.getAgentId().equals(agentId))
                                && (action == null || a.getActions().stream().anyMatch(x -> x.getName().equals(action)
                                    && (arguments == null || (validator != null && validator.isArgsValid(x.getParameters(), arguments)))))
                                && (stream == null || a.getStreams().stream().anyMatch(x -> x.getName().equals(stream)))
                        );
    }

    private ApiProxy getClient(String containerId, String token) {
        var url = containerClient.getUrl(containerId);
        return new ApiProxy(url, config.getOwnBaseUrl(), token);
    }

    private ApiProxy getPlatformClient(String url) {
        return new ApiProxy(url, config.getOwnBaseUrl(), null);
    }

    private ApiProxy getPlatformClient(String url, String token) {
        return new ApiProxy(url, config.getOwnBaseUrl(), token);
    }

    private String normalizeString(String string) {
        // string payload may or may not be enclosed in quotes -> normalize
        return string.trim().replaceAll("^\"|\"$", "");
    }

    private void checkUrl(String url) {
        try {
            new URI(url).toURL();
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
     * Creates a random String of length 24 containing upper and lower case characters and numbers
     */
    private String generateRandomPwd() {
        return RandomStringUtils.random(24, true, true);
    }


}
