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
import de.gtarc.opaca.model.AgentContainer.Connectivity;
import de.gtarc.opaca.platform.util.ArgumentValidator;
import de.gtarc.opaca.platform.util.RequirementsChecker;
import de.gtarc.opaca.util.ApiProxy;
import de.gtarc.opaca.util.WebSocketConnector;
import lombok.Getter;
import de.gtarc.opaca.util.EventHistory;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.web.server.ResponseStatusException;


/**
 * This class provides the actual implementation of the API routes. Might also be split up
 * further, e.g. for agent-forwarding, container-management, and linking to other platforms.
 *
 * Note that this class is closely related to the {@link RuntimePlatformApi} interface, without
 * actually implementing it. This is due to the access-token being passed as an explicit parameter
 * from the Rest-Controller for some routes where the current user is relevant, whereas for e.g.
 * the {@link ApiProxy} the access-token should always be handled "behind the scenes".
 */
@Log4j2
@Component
public class PlatformImpl {

    @Autowired
    private SessionData sessionData;

    @Autowired
    private PlatformConfig config;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TokenUserDetailsService userDetailsService;


    /** platform's own UUID */
    private final String platformId = UUID.randomUUID().toString();

    /** when the platform was started */
    private final ZonedDateTime startedAt = ZonedDateTime.now(ZoneId.of("Z"));

    private ContainerClient containerClient;

    /** Currently running Agent Containers, mapping container ID to description */
    private Map<String, AgentContainer> runningContainers;
    private Map<String, PostAgentContainer> startedContainers;
    private Map<String, String> tokens;

    /** Currently connected other Runtime Platforms, mapping URL to description */
    private Map<String, RuntimePlatform> connectedPlatforms;
    private Map<String, WebSocket> connectionWebsockets;

    /** Map of validators for validating action argument types for each container */
    private final Map<String, ArgumentValidator> validators = new HashMap<>();

    private final RequirementsChecker requirementsChecker = new RequirementsChecker(this);


    @PostConstruct
    public void initialize() {
        // restore session data
        this.runningContainers = sessionData.runningContainers;
        this.startedContainers = sessionData.startContainerRequests;
        this.tokens = sessionData.tokens;
        this.connectedPlatforms = sessionData.connectedPlatforms;
        this.connectionWebsockets = new HashMap<>();

        // initialize container client based on environment
        if (config.containerEnvironment == PostAgentContainer.ContainerEnvironment.DOCKER) {
            log.info("Using Docker on host {}", config.remoteDockerHost);
            this.containerClient = new DockerClient();
        } else if (config.containerEnvironment == PostAgentContainer.ContainerEnvironment.KUBERNETES) {
            log.info("Using Kubernetes with namespace {}", config.kubernetesNamespace);
            this.containerClient = new KubernetesClient();
        } else {
            throw new IllegalArgumentException("Invalid environment specified");
        }
        // test resolving own base URL and print result
        log.info("Own Base URL: {}", config.getOwnBaseUrl());

        this.containerClient.initialize(config, sessionData);
        this.containerClient.testConnectivity();

        for (var containerId : runningContainers.keySet()) {
            var image = runningContainers.get(containerId).getImage();
            validators.put(containerId, new ArgumentValidator(image));
        }
        for (var url : connectedPlatforms.keySet()) {
            openConnectionWebsocket(url, tokens.get(url));
        }
    }

    /*
     * PLATFORM INFO AND CONFIG
     */

    public RuntimePlatform getPlatformInfo() {
        return new RuntimePlatform(
                platformId,
                config.getOwnBaseUrl(),
                List.copyOf(runningContainers.values()),
                requirementsChecker.getFullPlatformProvisions(),
                List.copyOf(connectedPlatforms.keySet()),
                startedAt
        );
    }

    public Map<String, ?> getPlatformConfig() {
        return config.toMap();
    }

    public List<Event> getHistory() {
        return EventHistory.getInstance().getEvents();
    }

    public String login(Login loginParams) {
        return userDetailsService.generateTokenForUser(loginParams.getUsername(), loginParams.getPassword());
    }

    public String renewToken(String token) {
        // if auth is disabled, this produces "Username not found" and thus 403, which is a bit weird but okay...
        String owner = userDetailsService.getUser(jwtUtil.getUsernameFromToken(token)).getUsername();
        return jwtUtil.generateToken(owner, Duration.ofHours(10));
    }

    /*
     * AGENTS ROUTES
     */

    public List<AgentDescription> getAgents() {
        return streamAgents(false).collect(Collectors.toList());
    }

    public List<AgentDescription> getAllAgents() {
        return streamAgents(true).collect(Collectors.toList());
    }

    public AgentDescription getAgent(String agentId) {
        return streamAgents(true)
                .filter(a -> a.getAgentId().equals(agentId))
                .findAny().orElse(null);
    }

    public void send(String agentId, Message message, String containerId, boolean forward) throws IOException, NoSuchElementException {
        iterateClientMatches(
                getClients(containerId, agentId, null, null, null, forward),
                match -> {
                    match.getClient().send(agentId, message, containerId, false);
                    return null;
                },
                true
        );
    }

    public void broadcast(String channel, Message message, String containerId, boolean forward) throws IOException {
        iterateClientMatches(
                getClients(containerId, null, null, null, null, forward),
                match -> {
                    match.getClient().broadcast(channel, message, containerId, false);
                    return null;
                },
                false
        );
    }

    public JsonNode invoke(String action, Map<String, JsonNode> parameters, String agentId, int timeout, String containerId, boolean forward) throws IOException, NoSuchElementException {
        return iterateClientMatches(
                getClients(containerId, agentId, action, parameters, null, forward),
                match -> match.getClient().invoke(action, parameters, agentId, timeout, containerId, false),
                true
        );
    }

    public InputStream getStream(String stream, String agentId, String containerId, boolean forward) throws IOException {
        return iterateClientMatches(
                getClients(containerId, agentId, null, null, stream, forward),
                match -> match.getClient().getStream(stream, agentId, containerId, false),
                true
        );
    }

    public void postStream(String stream, byte[] inputStream, String agentId, String containerId, boolean forward) throws IOException {
        iterateClientMatches(
                getClients(containerId, agentId, null, null, stream, forward),
                match -> {
                    match.getClient().postStream(stream, inputStream, agentId, containerId, false);
                    return null;
                },
                true
        );
    }

    /*
     * CONTAINERS ROUTES
     */

    public String addContainer(PostAgentContainer postContainer, int timeout, String userToken) throws IOException {
        checkConfig(postContainer);
        checkRequirements(postContainer);
        String agentContainerId = UUID.randomUUID().toString();
        String token = "";
        String owner = "";
        if (config.enableAuth) {
            token = jwtUtil.generateToken(agentContainerId, Duration.ofHours(24));
            owner = jwtUtil.getUsernameFromToken(userToken);
        }
        // create user first so the container can immediately talk with the platform
        userDetailsService.createTempSubUser(agentContainerId, owner, null);

        // start container... this may raise an Exception, or returns the connectivity info
        Connectivity connectivity;
        try {
            connectivity = containerClient.startContainer(agentContainerId, token, owner, postContainer);
        } catch (Exception e) {
            userDetailsService.removeUser(agentContainerId);
            throw e;
        }

        // wait until container is up and running...
        var containerTimeout = System.currentTimeMillis() + (timeout > 0 ? timeout : config.containerTimeoutSec) * 1000L;
        var client = getClient(agentContainerId, token);
        String errorMessage = "Container did not respond with /info in time.";
        while (System.currentTimeMillis() < containerTimeout) {
            // check whether container is still starting or alive at all
            if (! containerClient.isContainerAlive(agentContainerId)) {
                errorMessage = "Container failed to start.";
                break;
            }
            try {
                // get container /info and add derived attributes
                var container = client.getContainerInfo();
                container.setConnectivity(connectivity);
                container.setOwner(owner);
                if (! container.getContainerId().equals(agentContainerId)) {
                    log.warn("Agent Container ID does not match: Expected {}, but found {}",
                            agentContainerId, container.getContainerId());
                }
                // register container in different collections
                runningContainers.put(agentContainerId, container);
                startedContainers.put(agentContainerId, postContainer);
                tokens.put(agentContainerId, token);
                validators.put(agentContainerId, new ArgumentValidator(container.getImage()));
                log.info("Container started: " + agentContainerId);
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
                log.error(e.getMessage());
            }
        }

        // if we reach this point, container did not start in time or does not provide /info route
        log.warn("Stopping Container. {}", errorMessage);
        try {
            containerClient.stopContainer(agentContainerId);
            userDetailsService.removeUser(agentContainerId);
        } catch (Exception e) {
            log.warn("Failed to stop container: {}", e.getMessage());
        }
        throw new IOException(errorMessage);
    }

    public String updateContainer(PostAgentContainer container, int timeout, String userToken) throws IOException {
        var matchingContainers = runningContainers.values().stream()
                .filter(c -> c.getImage().getImageName().equals(container.getImage().getImageName()))
                .toList();
        switch (matchingContainers.size()) {
            case 1: {
                var oldContainer = matchingContainers.get(0);
                removeContainer(oldContainer.getContainerId(), userToken);
                return addContainer(container, timeout, userToken);
            }
            case 0:
                throw new IllegalArgumentException("No matching container is currently running; please use POST instead.");
            default:
                throw new IllegalArgumentException("More than one matching container is currently running; please DELETE manually, then POST.");
        }
    }

    public List<AgentContainer> getContainers() {
        return List.copyOf(runningContainers.values());
    }

    public AgentContainer getContainer(String containerId) {
        return runningContainers.get(containerId);
    }

    public boolean removeContainer(String containerId, String userToken) throws IOException {
        AgentContainer container = runningContainers.get(containerId);
        if (config.enableAuth && userToken != null && ! userDetailsService.isAdminOrSelf(userToken, container.getOwner())) {
            // ignore if userToken == null; this is only the case iff the platform is about to shut down
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (container == null) return false;
        runningContainers.remove(containerId);
        startedContainers.remove(containerId);
        validators.remove(containerId);
        userDetailsService.removeUser(containerId);
        containerClient.stopContainer(containerId);
        return true;
    }

    /*
     * CONNECTIONS ROUTES
     */

    public boolean connectPlatform(ConnectionRequest connect, String userToken) throws IOException {
        String url = normalizeString(connect.getUrl());
        checkUrl(url);
        if (url.equals(config.getOwnBaseUrl()) || connectedPlatforms.containsKey(url)) {
            return false;
        }
        // try to get info (with token, if given)
        var token = connect.getToken();
        var client = getPlatformClient(url, token);
        var info = client.getPlatformInfo();
        // ask other platform to connect back to self?
        if (connect.isConnectBack()) {
            var ownUrl = config.getOwnBaseUrl();
            var ownToken = config.enableAuth ? jwtUtil.generateToken(url, Duration.ofDays(7)) : null;
            var owner = config.enableAuth ? jwtUtil.getUsernameFromToken(userToken) : null;
            userDetailsService.createTempSubUser(url, owner, Role.USER);
            client.connectPlatform(new ConnectionRequest(ownUrl, false, ownToken));
        }
        // use websocket to connect to updates
        openConnectionWebsocket(url, token);

        // store connection if all the above steps succeeded
        connectedPlatforms.put(url, info);
        tokens.put(url, token);
        return true;
    }

    public List<String> getConnections() {
        return List.copyOf(connectedPlatforms.keySet());
    }

    public boolean disconnectPlatform(ConnectionRequest disconnect) throws IOException {
        var url = normalizeString(disconnect.getUrl());
        checkUrl(url);
        if (connectedPlatforms.containsKey(url)) {
            connectedPlatforms.remove(url);
            tokens.remove(url);
            userDetailsService.removeUser(url);
            if (connectionWebsockets.containsKey(url)) {
                var ws = connectionWebsockets.remove(url);
                ws.sendClose(1000, "disconnected");
            }
            // disconnect other?
            if (disconnect.isConnectBack()) {
                var client = getPlatformClient(url, disconnect.getToken());
                var ownUrl = config.getOwnBaseUrl();
                client.disconnectPlatform(new ConnectionRequest(ownUrl, false, null));
            }
            log.info("Disconnected from {}", url);
            return true;
        }
        return false;
    }

    public boolean notifyUpdateContainer(String containerId) {
        containerId = normalizeString(containerId);
        if (! runningContainers.containsKey(containerId)) {
            var msg = String.format("Container did not exist: %s", containerId);
            throw new NoSuchElementException(msg);
        }
        try {
            var client = this.getClient(containerId, tokens.get(containerId));
            var containerInfo = client.getContainerInfo();
            containerInfo.setConnectivity(runningContainers.get(containerId).getConnectivity());
            runningContainers.put(containerId, containerInfo);
            validators.put(containerId, new ArgumentValidator(containerInfo.getImage()));
            return true;
        } catch (IOException e) {
            log.warn("Container did not respond: {}; removing...", containerId);
            runningContainers.remove(containerId);
            return false;
        }
    }

    public boolean notifyUpdatePlatform(String platformUrl) {
        platformUrl = normalizeString(platformUrl);
        checkUrl(platformUrl);
        if (platformUrl.equals(config.getOwnBaseUrl())) {
            log.warn("Cannot request update for self.");
            return false;
        }
        if (!connectedPlatforms.containsKey(platformUrl)) {
            var msg = String.format("Platform was not connected: %s", platformUrl);
            throw new NoSuchElementException(msg);
        }
        try {
            var client = getPlatformClient(platformUrl);
            var platformInfo = client.getPlatformInfo();
            connectedPlatforms.put(platformUrl, platformInfo);
            return true;
        } catch (IOException e) {
            log.warn("Platform did not respond: {}; removing...", platformUrl);
            connectedPlatforms.remove(platformUrl);
            return false;
        }
    }

    /*
     * HELPER METHODS
     */

    /**
     * Create Websocket connection and associate it with the connected platform's URL, to be closed when disconnected
     */
    private void openConnectionWebsocket(String url, String token) {
        try {
            var res = WebSocketConnector.subscribe(url, token, "/containers", msg -> notifyUpdatePlatform(url));
            connectionWebsockets.put(url, res.get());
        } catch (ExecutionException | InterruptedException e) {
            log.warn("Failed to establish websocket connection to {}", url);
        }
    }

    /**
     * Iterate over the provided ClientMatch stream, applying the given processor to all that are a full match.
     * The result of the first successful processor is returned.
     *
     * @param clientMatches The stream of ClientMatch objects.
     * @param callback A function that is applied to all eligible matches. Is allowed to throw IOException.
     * @param failOnNoMatch If true, throw a NoSuchElementException in case no client matched the requirements.
     * @return The result of the first successful processor function.
     * @throws NoSuchElementException In case no client matched the requirements
     * @throws IllegalArgumentException when a client matched the requirements but had mismatched action arguments.
     * @throws IOException In case all matching clients fail with this exception type.
     */
    private <T> T iterateClientMatches(
            Stream<ClientMatch> clientMatches,
            ThrowingFunction<ClientMatch, T> callback,
            boolean failOnNoMatch
    ) throws NoSuchElementException, IllegalArgumentException, IOException {
        ClientMatch mismatchedParamsClient = null;
        IOException lastException = null;

        for (ClientMatch match: (Iterable<? extends ClientMatch>) clientMatches::iterator) {
            if (match.isFullMatch()) {
                try {
                    return callback.apply(match);
                } catch (IOException e) {
                    log.warn("Exception from container: {}", e);
                    lastException = e;
                }
            } else if (match.isParamsMismatch()) {
                mismatchedParamsClient = match;
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        if (mismatchedParamsClient != null) {
            throw new IllegalArgumentException(String.format("Provided arguments %s do not match action parameters.", mismatchedParamsClient.actionArgs));
        }
        if (failOnNoMatch) {
            throw new NoSuchElementException("Requested resource not found.");
        } else {
            return null;
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
    private Stream<ClientMatch> getClients(String containerId, String agentId, String action, Map<String, JsonNode> parameters, String stream, boolean includeConnected) {
        // var clients = new HashMap<ApiProxy, MatchResult>();

        var localMatches = runningContainers.values().stream().map(container -> {
            var client = getClient(container.getContainerId(), tokens.get(container.getContainerId()));
            return new ClientMatch(containerId, agentId, action, parameters, stream)
                    .makeContainerMatch(container, client);
        });

        if (!includeConnected) return localMatches;

        var platformMatches = connectedPlatforms.entrySet().stream().map(entry -> {
            var client = getPlatformClient(entry.getKey(), tokens.get(entry.getKey()));
            return new ClientMatch(containerId, agentId, action, parameters, stream)
                    .makePlatformMatch(entry.getValue(), client);
        });

        return Stream.concat(localMatches, platformMatches);
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

    private void checkRequirements(PostAgentContainer request) {
        var failedRequirements = requirementsChecker.checkFailedRequirements(request.getImage());
        if (! failedRequirements.isEmpty()) {
            throw new IllegalArgumentException(String.format("Container Image has unsatisfied Requirements: %s",
                    failedRequirements));
        }
    }

    protected void testSelfConnection() throws Exception {
        var token = config.enableAuth ?
                userDetailsService.generateTokenForUser(config.platformAdminUser, config.platformAdminPwd) : null;
        var info = new ApiProxy(config.getOwnBaseUrl(), null, token, 5000).getPlatformInfo();
        if (! Objects.equals(platformId, info.getPlatformId())) {
            throw new IllegalArgumentException("Mismatched Platform ID");
        }
    }

    /**
     * This class retains information about the matching process for container and platform clients.
     * This information can then be queried to get information about the point of failure in the
     * matching process, e.g. to check if a client did not match due to missing the action, or due
     * to mismatched action arguments, etc.
     */
    private class ClientMatch {

        private final String containerId;
        private final String agentId;
        private final String actionName;
        private final Map<String, JsonNode> actionArgs;
        private final String streamName;

        private boolean containerMatch;
        private boolean agentMatch;
        private boolean actionMatch;
        private boolean paramsMatch;
        private boolean streamMatch;

        @Getter
        private ApiProxy client = null;

        private ArgumentValidator validator = null;

        public ClientMatch(String containerId, String agentId, String actionName, Map<String, JsonNode> actionArgs, String streamName) {
            this.containerId = containerId;
            this.agentId = agentId;
            this.actionName = actionName;
            this.actionArgs = actionArgs;
            this.streamName = streamName;
            // initialize matches to true for wildcards / irrelevant attributes
            this.containerMatch = containerId == null;
            this.agentMatch = agentId == null;
            this.actionMatch = actionName == null;
            this.paramsMatch = actionArgs == null;
            this.streamMatch = streamName == null;
        }

        /**
         * Check if the given container fulfills the matching parameters.
         */
        public ClientMatch makeContainerMatch(AgentContainer container, ApiProxy client) {
            if (client != null) {
                this.client = client;
            }
            this.validator = validators.containsKey(container.getContainerId())
                    ? validators.get(container.getContainerId())
                    : new ArgumentValidator(container.getImage());
            if (containerId == null || container.getContainerId().equals(containerId)) {
                containerMatch = true;
                checkAgentMatch(container);
            }
            return this;
        }

        /**
         * Check if the given platform fulfills the matching parameters.
         */
        public ClientMatch makePlatformMatch(RuntimePlatform runtimePlatform, ApiProxy client) {
            this.client = client;
            for (var container : runtimePlatform.getContainers()) {
                makeContainerMatch(container, null);
            }
            return this;
        }

        public boolean isFullMatch() {
            return containerMatch && agentMatch && actionMatch && paramsMatch && streamMatch;
        }

        public boolean isParamsMismatch() {
            return containerMatch && agentMatch && actionMatch && !paramsMatch;
        }

        public String getParamsDescription() {
            return String.format("containerId=%s, agentId=%s, actionName=%s, actionArgs=%s, streamName=%s", containerId, agentId, actionName, actionArgs, streamName);
        }

        public String getMatchDescription() {
            return String.format("containerMatch=%s, agentMatch=%s, actionMatch=%s, paramsMatch=%s, streamMatch=%s", containerMatch, agentMatch, actionMatch, paramsMatch, streamMatch);
        }

        private void checkAgentMatch(AgentContainer container) {
            for (var agent : container.getAgents()) {
                if (agentId == null || agent.getAgentId().equals(agentId)) {
                    agentMatch = true;
                    if (checkActionMatch(agent) && checkStreamMatch(agent)) {
                        break;
                    }
                }
            }
        }

        private boolean checkActionMatch(AgentDescription agent) {
            for (var action : agent.getActions()) {
                if (action.getName().equals(actionName)) {
                    actionMatch = true;
                    if (checkParamsMatch(action)) {
                        break;
                    }
                }
            }
            return actionMatch;
        }

        private boolean checkParamsMatch(Action action) {
            if (actionArgs != null && (validator == null || validator.isArgsValid(action.getParameters(), actionArgs))) {
                paramsMatch = true;
            }
            return paramsMatch;
        }

        private boolean checkStreamMatch(AgentDescription agent) {
            for (var stream : agent.getStreams()) {
                if (stream.getName().equals(streamName)) {
                    streamMatch = true;
                    break;
                }
            }
            return streamMatch;
        }
    }

    /**
     * A simple interface to define a lambda function that may throw an IOException.
     *
     * @param <T> The type of the lambda's single argument.
     * @param <R> The type of the lambda's return value.
     */
    private interface ThrowingFunction<T, R> {
        R apply(T t) throws IOException;
    }

}
