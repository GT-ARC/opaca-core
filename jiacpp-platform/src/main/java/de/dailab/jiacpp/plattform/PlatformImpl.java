package de.dailab.jiacpp.plattform;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import de.dailab.jiacpp.api.AgentContainerApi;
import de.dailab.jiacpp.api.RuntimePlatformApi;
import de.dailab.jiacpp.model.*;
import de.dailab.jiacpp.util.RestHelper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.java.Log;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Duration;
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

    // TODO later, move all docker-specific stuff to separate class

    final PlatformConfig config;


    /** Currently running Agent Containers, mapping container ID to description */
    private final Map<String, AgentContainer> runningContainers = new HashMap<>();

    /** additional Docker-specific information on agent containers */
    private final Map<String, DockerContainerInfo> dockerContainers = new HashMap<>();

    /** Currently connected other Runtime Platforms, mapping URL to description */
    private final Map<String, RuntimePlatform> connectedPlatforms = new HashMap<>();

    /** Set of remote Runtime Platform URLs with a pending connection request */
    private final Set<String> pendingConnections = new HashSet<>();


    /** Client for accessing (remote) Docker runtime */
    private final DockerClient dockerClient;


    @Data @AllArgsConstructor
    static class DockerContainerInfo {
        String containerId;
        String internalIp;
    }


    public PlatformImpl(PlatformConfig config) {
        this.config = config;

        // TODO get config/settings, e.g.
        //  - docker settings, e.g. remote docker, gpu support, ...

        DockerClientConfig dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("unix:///var/run/docker.sock")
                //.withDockerTlsVerify(true)
                //.withDockerCertPath("/home/user/.docker")
                //.withRegistryUsername(registryUser)
                //.withRegistryPassword(registryPass)
                //.withRegistryEmail(registryMail)
                //.withRegistryUrl(registryUrl)
                .build();

        DockerHttpClient dockerHttpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(dockerConfig.getDockerHost())
                .sslConfig(dockerConfig.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        dockerClient = DockerClientImpl.getInstance(dockerConfig, dockerHttpClient);
    }

    @Override
    public RuntimePlatform getInfo() throws IOException {
        return new RuntimePlatform(
                getOwnBaseUrl(),
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
    public void send(String agentId, Message message) throws IOException {
        var client = getClient(null, agentId, null);
        if (client != null) {
            log.info("Forwarding /send to " + client.baseUrl);
            client.post(String.format("/send/%s", agentId), message, null);
        } else {
            throw new NoSuchElementException(String.format("Not found: agent '%s'", agentId));
        }
    }

    @Override
    public void broadcast(String channel, Message message) throws IOException {
        for (AgentContainer container : runningContainers.values()) {
            getClient(container).post(String.format("/broadcast/%s", channel), message, null);
        }
        // TODO how to handle IO Exception forwarding to a single container/platform, if broadcast to all others worked?
        // TODO broadcast to other platforms... how to prevent infinite loops? optional flag parameter?
    }

    @Override
    public JsonNode invoke(String action, Map<String, JsonNode> parameters) throws IOException {
        return invoke(null, action, parameters);
    }

    @Override
    public JsonNode invoke(String agentId, String action, Map<String, JsonNode> parameters) throws IOException {
        var client = getClient(null, agentId, action);
        if (client != null) {
            log.info("Forwarding /invoke to " + client.baseUrl);
            var url = agentId == null
                    ? String.format("/invoke/%s", action)
                    : String.format("/invoke/%s/%s", action, agentId);
            return client.post(url, parameters, JsonNode.class);
        } else {
            throw new NoSuchElementException(String.format("Not found: action '%s' @ agent '%s'", action, agentId));
        }
    }

    /*
     * CONTAINERS ROUTES
     */

    @Override
    public String addContainer(AgentContainerImage image) throws IOException {
        String agentContainerId = UUID.randomUUID().toString();

        try {
            // pull image if not present
            if (dockerClient.listImagesCmd().withImageNameFilter(image.getImageName()).exec().isEmpty()) {
                log.info("Pulling Image..." + image.getImageName());
                try {
                    dockerClient.pullImageCmd(image.getImageName()).exec(new PullImageResultCallback())
                            .awaitCompletion();
                } catch (InterruptedException e) {
                    log.warning(e.getMessage());
                }
            }

            log.info("Creating Container...");
            CreateContainerResponse res = dockerClient.createContainerCmd(image.getImageName())
                    .withEnv(
                            String.format("%s=%s", AgentContainerApi.ENV_CONTAINER_ID, agentContainerId),
                            String.format("%s=%s", AgentContainerApi.ENV_PLATFORM_URL, getOwnBaseUrl()))
                    .exec();
            log.info(String.format("Result: %s", res));

            log.info("Starting Container...");
            dockerClient.startContainerCmd(res.getId()).exec();

            // TODO get internal IP... why is this deprecated?
            InspectContainerResponse info = dockerClient.inspectContainerCmd(res.getId()).exec();
            dockerContainers.put(agentContainerId, new DockerContainerInfo(res.getId(), info.getNetworkSettings().getIpAddress()));

        } catch (NotFoundException e) {
            log.warning("Image not found: " + image.getImageName());
            throw new NoSuchElementException("Image not found: " + image.getImageName());
        }

        var start = System.currentTimeMillis();
        var client = getClient(agentContainerId);
        while (System.currentTimeMillis() < start + config.containerTimeoutSec * 1000) {
            try {
                var container = client.get("/info", AgentContainer.class);
                runningContainers.put(agentContainerId, container);
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

        if (runningContainers.containsKey(agentContainerId)) {
            log.info("Container started: " + agentContainerId);
            return agentContainerId;
        } else {
            try {
                var dockerId = dockerContainers.get(agentContainerId).getContainerId();
                dockerClient.stopContainerCmd(dockerId).exec();
            } catch (DockerException e) {
                log.warning("Failed to stop container: " + e.getMessage());
            }
            throw new IOException("Container did not respond to API calls in time; stopped.");
        }
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
            var dockerId = dockerContainers.get(containerId).getContainerId();
            dockerClient.stopContainerCmd(dockerId).exec();
            runningContainers.remove(containerId);
            // TODO get result, check if and when it is finally stopped?
            // TODO handle case of container already being terminated (due to error or similar)
            // TODO possibly that the container refuses being stopped? call "kill" instead? how to test this?
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
        if (url.equals(getOwnBaseUrl()) || connectedPlatforms.containsKey(url)) {
            return false;
        } else if (pendingConnections.contains(url)) {
            // callback from remote platform following our own request for connection
            return true;
        } else {
            try {
                pendingConnections.add(url);
                var client = new RestHelper(url);
                var res = client.post("/connections", getOwnBaseUrl(), Boolean.class);
                if (res) {
                    var info = client.get("/info", RuntimePlatform.class);
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
            var client = new RestHelper(url);
            var res = client.delete("/connections", getOwnBaseUrl(), Boolean.class);
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
     * Get Host IP address. Should return preferred outbound address.
     * Adapted from https://stackoverflow.com/a/38342964/1639625
     */
    private String getOwnBaseUrl() {
        if (config.publicUrl != null) {
            return config.publicUrl;
        }
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            String host = socket.getLocalAddress().getHostAddress();
            return "http://" + host + ":" + config.serverPort;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get client for send or invoke to specific agent, in a specific container, for a specific action.
     * All those parameters are optional (e.g. for "send" or "invoke" without agentId), but obviously
     * _some_ should be set, otherwise it does not make much sense to call the method.
     */
    private RestHelper getClient(String containerId, String agentId, String action) throws IOException {
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
            return new RestHelper(platform.get().getBaseUrl());
        }
        return null;
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

    private RestHelper getClient(AgentContainer container) {
        return getClient(container.getContainerId());
    }

    private RestHelper getClient(String containerId) {
        var ip = dockerContainers.get(containerId).getInternalIp();
        return new RestHelper(String.format("http://%s:%s", ip, AgentContainerApi.DEFAULT_PORT));
    }

    private String normalizeUrl(String url) {
        // string payload may or may not be enclosed in quotes -> normalize
        return url.trim().replaceAll("^\"|\"$", "");
    }

}
