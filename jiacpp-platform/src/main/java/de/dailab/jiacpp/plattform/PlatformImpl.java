package de.dailab.jiacpp.plattform;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
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
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class provides the actual implementation of the API routes. Might also be split up
 * further, e.g. for agent-forwarding, container-management, and linking to other platforms.
 */
@Log
public class PlatformImpl implements RuntimePlatformApi {

    // TODO properly describe the different routes and their expected behaviour somewhere,
    //  including error cases (not found?), forwarding to containers and remote platforms, etc.

    // TODO if not found on own AgentContainers, all the Container Routes should
    //  also check connected platforms next... not just forward the request to connected platforms,
    //  but check which platform actually has the desired agent or action and then send it there

    // TODO make sure agent IDs are globally unique? extend agent-ids with platform-hash or similar?
    //  e.g. optionally allow "agentId@containerId" to be passed in place of agentId for all routes?

    // TODO later, move all docker-specific stuff to separate class


    public static final RuntimePlatformApi INSTANCE = new PlatformImpl();


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


    private PlatformImpl() {
        // TODO get config/settings, e.g.
        //  - time before updating containers or remote platforms
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
        updateContainers();
        return new RuntimePlatform(
                getOwnBaseUrl(),
                List.copyOf(runningContainers.values()),
                List.of("nothing"),
                List.of()
        );
    }

    /*
     * AGENTS ROUTES
     */

    @Override
    public List<AgentDescription> getAgents() throws IOException {
        updateContainers();
        return runningContainers.values().stream()
                .flatMap(c -> c.getAgents().stream())
                .collect(Collectors.toList());
    }

    @Override
    public AgentDescription getAgent(String agentId) throws IOException {
        updateContainers();
        return runningContainers.values().stream()
                .flatMap(c -> c.getAgents().stream())
                .filter(a -> a.getAgentId().equals(agentId))
                .findAny().orElse(null);
    }

    @Override
    public void send(String agentId, Message message) throws IOException {
        updateContainers();
        var container = runningContainers.values().stream()
                .filter(c -> c.getAgents().stream()
                        .anyMatch(a -> a.getAgentId().equals(agentId)))
                .findAny().orElse(null);
        if (container != null) {
            getClient(container).post(String.format("/send/%s", agentId), message, null);
        } else {
            // TODO check connected platforms
        }
    }

    @Override
    public void broadcast(String channel, Message message) throws IOException {
        updateContainers();
        for (AgentContainer container : runningContainers.values()) {
            getClient(container).post(String.format("/broadcast/%s", channel), message, null);
        }
        // TODO broadcast to other platforms... how to prevent infinite loops? optional flag parameter?
    }

    @Override
    public JsonNode invoke(String action, Map<String, JsonNode> parameters) throws IOException {
        return invoke(null, action, parameters);
    }

    @Override
    public JsonNode invoke(String agentId, String action, Map<String, JsonNode> parameters) throws IOException {
        updateContainers();
        // TODO check that parameters match
        var container = runningContainers.values().stream()
                .filter(c -> c.getAgents().stream()
                        .anyMatch(a -> (agentId == null || a.getAgentId().equals(agentId)) &&
                                a.getActions().stream().anyMatch(x -> x.getName().equals(action))))
                .findAny().orElse(null);
        if (container != null) {
            if (agentId == null) {
                return getClient(container).post(String.format("/invoke/%s", action),
                        parameters, JsonNode.class);
            } else {
                return getClient(container).post(String.format("/invoke/%s/%s", action, agentId),
                        parameters, JsonNode.class);
            }
        } else {
            // TODO check connected platforms
        }
        return null;
    }

    /*
     * CONTAINERS ROUTES
     */

    @Override
    public String addContainer(AgentContainerImage container) throws IOException {
        //log.info("Pulling Image...");
        //dockerClient.pullImageCmd(container.getImageName()).start();

        String agentContainerId = UUID.randomUUID().toString();

        log.info("Creating Container...");
        CreateContainerResponse res = dockerClient.createContainerCmd(container.getImageName())
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
        runningContainers.put(agentContainerId, null); // to be updated later

        return agentContainerId;
    }

    @Override
    public List<AgentContainer> getContainers() throws IOException {
        updateContainers();
        return List.copyOf(runningContainers.values());
    }

    @Override
    public AgentContainer getContainer(String containerId) throws IOException {
        updateContainers();
        return runningContainers.values().stream()
                .filter(c -> c.getContainerId().equals(containerId))
                .findAny().orElse(null);
    }

    @Override
    public boolean removeContainer(String containerId) throws IOException {
        updateContainers();
        AgentContainer container = runningContainers.get(containerId);
        if (container != null) {
            var dockerId = dockerContainers.get(containerId).getContainerId();
            dockerClient.stopContainerCmd(dockerId).exec();
            // TODO get result, check if and when it is finally stopped?
            return true;
        }
        return false;
    }

    /*
     * CONNECTIONS ROUTES
     */

    @Override
    public boolean connectPlatform(String url) throws IOException {
        // TODO not yet tested
        if (pendingConnections.contains(url)) {
            // callback from remote platform following our own request for connection
            return true;
        } else {
            pendingConnections.add(url);
            var client = new RestHelper(url);
            var res = client.post("/connections", url, Boolean.class);
            if (res) {
                pendingConnections.remove(url);
                var info = client.get("/info", RuntimePlatform.class);
                if (info != null) {
                    connectedPlatforms.put(url, info);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public List<String> getConnections() {
        return List.copyOf(connectedPlatforms.keySet());
    }

    @Override
    public boolean disconnectPlatform(String url) throws IOException {
        if (connectedPlatforms.containsKey(url)) {
            var client = new RestHelper(url);
            var res = client.delete("/connections", url, Boolean.class);
            log.info(String.format("Disconnected from %s: %s", url, res));
            return true;
        }
        return false;
    }

    /*
     * HELPER METHODS
     */

    private String getOwnBaseUrl() {
        // TODO for testing, hardcoded my current DAI intranet IP address...
        return "http://10.1.1.8:8000";
    }

    private RestHelper getClient(AgentContainer container) {
        return getClient(container.getContainerId());
    }

    private RestHelper getClient(String containerId) {
        var ip = dockerContainers.get(containerId).getInternalIp();
        return new RestHelper(String.format("http://%s:%s", ip, AgentContainerApi.DEFAULT_PORT));
    }

    private void updateContainers() throws IOException {
        // TODO check last update time for all containers, get new info after some time
        for (String id : runningContainers.keySet()) {
            if (runningContainers.get(id) == null) {
                var container = getClient(id).get("/info", AgentContainer.class);
                runningContainers.put(id, container);
            }
        }
    }

}
