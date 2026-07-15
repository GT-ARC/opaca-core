package de.gtarc.opaca.platform.services;

import com.fasterxml.jackson.databind.JsonMappingException;
import de.gtarc.opaca.api.AgentContainerApi;
import de.gtarc.opaca.api.ContainersApi;
import de.gtarc.opaca.model.AgentContainer;
import de.gtarc.opaca.model.Login;
import de.gtarc.opaca.model.PostAgentContainer;
import de.gtarc.opaca.platform.PlatformConfig;
import de.gtarc.opaca.platform.auth.AuthUtils;
import de.gtarc.opaca.platform.containerclient.ContainerClient;
import de.gtarc.opaca.platform.containerclient.DockerClient;
import de.gtarc.opaca.platform.containerclient.KubernetesClient;
import de.gtarc.opaca.platform.session.SessionData;
import de.gtarc.opaca.platform.util.ArgumentValidator;
import de.gtarc.opaca.platform.util.Utils;
import de.gtarc.opaca.util.ApiProxy;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import de.gtarc.opaca.platform.event.ContainerChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;

/**
 * Implementation of ContainerApi, responsible for starting and stopping OPACA Agent Containers.
 */
@Log4j2
@Service
public class ContainersService implements ContainersApi {

    @Autowired
    private SessionData sessionData;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformConfig config;

    @Autowired
    private AuthUtils authUtils;

    @Autowired
    private PlatformService platformService;

    /** client to the container management API (Docker or Kubernetes) */
    private ContainerClient containerClient;

    /** Map of validators for validating action argument types for each container */
    private final Map<String, ArgumentValidator> validators = new HashMap<>();

    /** internal flag; set to true on shutdown to allow stopping containers without necessary credentials */
    private boolean isShuttingDown = false;

    /*
     * LIFE CYCLE
     */

    @PostConstruct
    public void initialize() {
        // initialize the container client based on the environment
        if (config.containerEnvironment == PostAgentContainer.ContainerEnvironment.DOCKER) {
            log.info("Using Docker on host {}", config.remoteDockerHost);
            this.containerClient = new DockerClient();
        } else if (config.containerEnvironment == PostAgentContainer.ContainerEnvironment.KUBERNETES) {
            log.info("Using Kubernetes with namespace {}", config.kubernetesNamespace);
            this.containerClient = new KubernetesClient();
        } else {
            log.fatal("Invalid environment specified");
            System.exit(1);
        }
        this.containerClient.initialize(config, sessionData);
        this.containerClient.testConnectivity();
    }

    /*
     * API ROUTES
     */

    @Override
    public String addContainer(PostAgentContainer postContainer, int timeout) throws IOException {
        checkConfig(postContainer);
        checkRequirements(postContainer);
        String agentContainerId = UUID.randomUUID().toString();
        Map<String, String> env = new HashMap<>();
        env.put(AgentContainerApi.ENV_OWNER, authUtils.getRequestUser());
        String owner = authUtils.getRequestUser();
        if (config.requireAuth) {
            var clientSecret = authUtils.createClientAndGetSecret(agentContainerId, "OPACA AC of RP " + authUtils.platformId);
            env.put(AgentContainerApi.ENV_KEYCLOAK_URL, config.keycloakIssuerUri);
            env.put(AgentContainerApi.ENV_CLIENT_SECRET, clientSecret);
        }

        // start the container... this may raise an Exception, or returns the connectivity info
        AgentContainer.Connectivity connectivity;
        try {
            connectivity = containerClient.startContainer(agentContainerId, postContainer, env);
        } catch (Exception e) {
            authUtils.deleteClient(agentContainerId);
            throw e;
        }

        // wait until the container is up and running...
        var containerTimeout = System.currentTimeMillis() + (timeout > 0 ? timeout : config.containerTimeoutSec) * 1000L;
        var client = getContainerProxy(agentContainerId);
        String errorMessage = "Container did not respond with /info in time.";
        while (System.currentTimeMillis() < containerTimeout) {
            // check whether the container is still starting or alive at all
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
                sessionData.runningContainers.put(agentContainerId, container);
                sessionData.startContainerRequests.put(agentContainerId, postContainer);
                //tokens.put(agentContainerId, token);
                validators.put(agentContainerId, new ArgumentValidator(container.getImage()));
                log.info("Container started: {}", agentContainerId);
                eventPublisher.publishEvent(new ContainerChangedEvent(this, agentContainerId, ContainerChangedEvent.Type.ADDED));
                return agentContainerId;
            } catch (JsonMappingException e) {
                errorMessage = "Container returned malformed /info: " + e.getMessage();
                break;
            } catch (IOException e) {
                // this is normal... waiting for the container to start and provide services
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }
        }

        // if we reach this point, the container did not start in time or does not provide /info route
        log.warn("Stopping Container. {}", errorMessage);
        try {
            containerClient.stopContainer(agentContainerId);
            authUtils.deleteClient(agentContainerId);
        } catch (Exception e) {
            log.warn("Failed to stop container: {}", e.getMessage());
        }
        throw new IOException(errorMessage);
    }

    @Override
    public String updateContainer(PostAgentContainer container, int timeout) throws IOException {
        var matchingContainers = sessionData.runningContainers.values().stream()
                .filter(c -> c.getImage().getImageName().equals(container.getImage().getImageName()))
                .toList();
        switch (matchingContainers.size()) {
            case 1: {
                var oldContainer = matchingContainers.getFirst();
                removeContainer(oldContainer.getContainerId());
                return addContainer(container, timeout);
            }
            case 0:
                throw new IllegalArgumentException("No matching container is currently running; please use POST instead.");
            default:
                throw new IllegalArgumentException("More than one matching container is currently running; please DELETE manually, then POST.");
        }
    }

    @Override
    public List<AgentContainer> getContainers() {
        return sessionData.streamContainers(false).toList();
    }


    @Override
    public AgentContainer getContainer(String containerId) {
        return sessionData.runningContainers.get(containerId);
    }

    @Override
    public boolean removeContainer(String containerId) throws IOException {
        AgentContainer container = sessionData.runningContainers.get(containerId);
        if (config.requireAuth && ! isShuttingDown && ! authUtils.isAdminOrSelf(container.getOwner())) {
            // ignore if userToken == null; this is only the case iff the platform is about to shut down
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (container == null) return false;
        sessionData.runningContainers.remove(containerId);
        sessionData.startContainerRequests.remove(containerId);
        validators.remove(containerId);
        authUtils.deleteClient(containerId);
        containerClient.stopContainer(containerId);
        eventPublisher.publishEvent(new ContainerChangedEvent(this, containerId, ContainerChangedEvent.Type.REMOVED));
        return true;
    }

    @Override
    public boolean notifyUpdateContainer(String containerId) {
        containerId = Utils.normalizeString(containerId);
        if (! sessionData.runningContainers.containsKey(containerId)) {
            var msg = String.format("Container did not exist: %s", containerId);
            throw new NoSuchElementException(msg);
        }
        try {
            var client = getContainerProxy(containerId);
            var containerInfo = client.getContainerInfo();
            containerInfo.setConnectivity(sessionData.runningContainers.get(containerId).getConnectivity());
            sessionData.runningContainers.put(containerId, containerInfo);
            validators.put(containerId, new ArgumentValidator(containerInfo.getImage()));
            eventPublisher.publishEvent(new ContainerChangedEvent(this, containerId, ContainerChangedEvent.Type.UPDATED));
            return true;
        } catch (IOException e) {
            log.warn("Container did not respond: {}; removing...", containerId);
            sessionData.runningContainers.remove(containerId);
            eventPublisher.publishEvent(new ContainerChangedEvent(this, containerId, ContainerChangedEvent.Type.REMOVED));
            return false;
        }
    }

    @Override
    public String containerLogin(String containerId, Login loginParams) throws IOException {
        if (! sessionData.runningContainers.containsKey(containerId)) {
            throw new NoSuchElementException("Container not found: " + containerId);
        }
        // find matching user and container
        var user = authUtils.getRequestUser();
        var client = getContainerProxy(containerId);
        // get container-login-token, associate with user
        String token = client.containerLogin(loginParams);
        authUtils.addContainerToken(user, containerId, token);
        return token;
    }

    @Override
    public boolean containerLogout(String containerId) throws IOException {
        if (! sessionData.runningContainers.containsKey(containerId)) {
            throw new NoSuchElementException("Container not found: " + containerId);
        }
        var token = authUtils.removeContainerToken(authUtils.getRequestUser(), containerId);
        return token != null && getContainerProxy(containerId)
                .withExtraHeaders(Map.of(AgentContainerApi.HEADER_TOKEN, token))
                .containerLogout();
    }

    /*
     * HELPER METHODS
     */

    protected ApiProxy getContainerProxy(String containerId) {
        var url = containerClient.getUrl(containerId);
        String token = authUtils.getPlatformToken();
        return new ApiProxy(url, config.getOwnBaseUrl(), token);
    }

    protected ArgumentValidator getValidator(AgentContainer container) {
        if (! validators.containsKey(container.getContainerId())) {
            validators.put(container.getContainerId(), new ArgumentValidator(container.getImage()));
        }
        return validators.get(container.getContainerId());
    }

    private void checkConfig(PostAgentContainer request) {
        if (request.getClientConfig() != null && request.getClientConfig().getType() != config.containerEnvironment) {
            throw new IllegalArgumentException(String.format("Client Config %s does not match Container Environment %s",
                    request.getClientConfig().getType(), config.containerEnvironment));
        }
    }

    private void checkRequirements(PostAgentContainer request) {
        var failedRequirements = platformService.checkFailedRequirements(request.getImage());
        if (! failedRequirements.isEmpty()) {
            throw new IllegalArgumentException(String.format("Container Image has unsatisfied Requirements: %s",
                    failedRequirements));
        }
    }

    public void setIsShuttingDown() {
        this.isShuttingDown = true;
    }

}
