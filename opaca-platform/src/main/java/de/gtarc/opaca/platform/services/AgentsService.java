package de.gtarc.opaca.platform.services;

import com.fasterxml.jackson.databind.JsonNode;
import de.gtarc.opaca.api.AgentContainerApi;
import de.gtarc.opaca.api.AgentsApi;
import de.gtarc.opaca.model.*;
import de.gtarc.opaca.platform.auth.AuthUtils;
import de.gtarc.opaca.platform.session.SessionData;
import de.gtarc.opaca.platform.util.ArgumentValidator;
import de.gtarc.opaca.util.ApiProxy;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of AgentsApi, responsible for forwarding API calls to the respective Agent Containers.
 */
@Log4j2
@Service
public class AgentsService implements AgentsApi {

    @Autowired
    private SessionData sessionData;

    @Autowired
    private AuthUtils authUtils;

    @Autowired
    private ConnectionsService connectionsService;

    @Autowired
    private ContainersService containersService;

    /*
     * API ROUTES
     */

    @Override
    public List<AgentDescription> getAgents() {
        return sessionData.streamAgents(false).collect(Collectors.toList());
    }

    @Override
    public AgentDescription getAgent(String agentId) {
        return sessionData.streamAgents(true)
                .filter(a -> a.getAgentId().equals(agentId))
                .findAny().orElse(null);
    }

    @Override
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

    @Override
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

    @Override
    public JsonNode invoke(String action, Map<String, JsonNode> parameters, String agentId, int timeout, String containerId, boolean forward) throws IOException, NoSuchElementException {
        return iterateClientMatches(
                getClients(containerId, agentId, action, parameters, null, forward),
                match -> match.getClientForUser().invoke(action, parameters, agentId, timeout, containerId, false),
                true
        );
    }

    @Override
    public InputStream getStream(String stream, String agentId, String containerId, boolean forward) throws IOException {
        return iterateClientMatches(
                getClients(containerId, agentId, null, null, stream, forward),
                match -> match.getClient().getStream(stream, agentId, containerId, false),
                true
        );
    }

    @Override
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
     * HELPER METHODS
     */

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
            java.util.stream.Stream<ClientMatch> clientMatches,
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
                    log.warn("Exception from container", e);
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
    private java.util.stream.Stream<ClientMatch> getClients(String containerId, String agentId, String action, Map<String, JsonNode> parameters, String stream, boolean includeConnected) {
        // var clients = new HashMap<ApiProxy, MatchResult>();

        var localMatches = sessionData.runningContainers.values().stream().map(container -> {
            var client = containersService.getContainerProxy(container.getContainerId());
            return new ClientMatch(containerId, agentId, action, parameters, stream)
                    .makeContainerMatch(container, client);
        });

        if (!includeConnected) return localMatches;

        var platformMatches = sessionData.connectedPlatforms.entrySet().stream().map(entry -> {
            var client = connectionsService.getPlatformProxy(entry.getKey(), authUtils.getPlatformToken());
            return new ClientMatch(containerId, agentId, action, parameters, stream)
                    .makePlatformMatch(entry.getValue(), client);
        });

        return Stream.concat(localMatches, platformMatches);
    }

    /**
     * This class retains information about the matching process for container and platform clients.
     * This information can then be queried to get information about the point of failure in the
     * matching process, e.g., to check if a client did not match due to missing the action, or due
     * to mismatched action arguments, etc.
     */
    private class ClientMatch {

        // the values that have to match (null being "any")
        private final String containerId;
        private final String agentId;
        private final String actionName;
        private final Map<String, JsonNode> actionArgs;
        private final String streamName;

        // whether the above values match
        private boolean containerMatch;
        private boolean agentMatch;
        private boolean actionMatch;
        private boolean paramsMatch;
        private boolean streamMatch;

        // the actual containerId this client is using, or null for a platform client
        private String actualContainerId = null;

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
            this.actualContainerId = container.getContainerId();
            if (client != null) {
                this.client = client;
            }
            this.validator = containersService.getValidator(container);
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

        public ApiProxy getClientForUser() {
            // redirect to another platform
            if (actualContainerId == null) {
                return client;
            }
            var containerLoginToken = authUtils.getContainerToken(authUtils.getRequestUser(), actualContainerId);
            // not logged in to container
            if (containerLoginToken == null) {
                return client;
            }
            // get ApiProxy with the required container login token header
            return client.withExtraHeaders(Map.of(AgentContainerApi.HEADER_TOKEN, containerLoginToken));
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
