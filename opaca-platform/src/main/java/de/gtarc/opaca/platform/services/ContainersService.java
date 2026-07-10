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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;

@Log4j2
@Component
public class ContainersService implements ContainersApi {

    @Autowired
    private SessionData sessionData;

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

    /*
     * LIFE CYCLE
     */

    @PostConstruct
    public void initialize() {
        // initialize container client based on environment
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
        // test resolving own base URL and print result
        log.info("Own Base URL: {}", config.getOwnBaseUrl());

        this.containerClient.initialize(config, sessionData);
        this.containerClient.testConnectivity();

        for (var containerId : sessionData.runningContainers.keySet()) {
            var image = sessionData.runningContainers.get(containerId).getImage();
            validators.put(containerId, new ArgumentValidator(image));
        }
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

        // start container... this may raise an Exception, or returns the connectivity info
        AgentContainer.Connectivity connectivity;
        try {
            connectivity = containerClient.startContainer(agentContainerId, postContainer, env);
        } catch (Exception e) {
            authUtils.deleteClient(agentContainerId);
            throw e;
        }

        // wait until container is up and running...
        var containerTimeout = System.currentTimeMillis() + (timeout > 0 ? timeout : config.containerTimeoutSec) * 1000L;
        var client = getContainerProxy(agentContainerId);
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
                sessionData.runningContainers.put(agentContainerId, container);
                sessionData.startContainerRequests.put(agentContainerId, postContainer);
                //tokens.put(agentContainerId, token);
                validators.put(agentContainerId, new ArgumentValidator(container.getImage()));
                log.info("Container started: {}", agentContainerId);
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
                var oldContainer = matchingContainers.get(0);
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
            return true;
        } catch (IOException e) {
            log.warn("Container did not respond: {}; removing...", containerId);
            sessionData.runningContainers.remove(containerId);
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
        return validators.containsKey(container.getContainerId())
                ? validators.get(container.getContainerId())
                : new ArgumentValidator(container.getImage());
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



    /*
     * TEMP STUFF (this should be changed, maybe dissolve Session entirely and move the bits to the individual services?
     */

    /** internal flag; set to true on shutdown to allow stopping containers without necessary credentials */
    private boolean isShuttingDown = false;

    public void setIsShuttingDown() {
        this.isShuttingDown = true;
    }

}
