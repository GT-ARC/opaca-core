package de.dailab.jiacpp.plattform;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import de.dailab.jiacpp.api.RuntimePlatformApi;
import de.dailab.jiacpp.model.*;
import de.dailab.jiacpp.util.RestHelper;
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

    // TODO behaviour on "not found" should probably rather be to raise an exception,
    //  not return null or do nothing

    // TODO move all the trace-logging (which method is called) to the REST Controller

    // TODO if not found on own AgentContainers, all the Container Routes should
    //  also check connected platforms next... not just forward the request to connected platforms,
    //  but check which platform actually has the desired agent or action and then send it there

    // TODO make sure agent IDs are globally unique? extend agent-ids with platform-hash or similar?

    public static final RuntimePlatformApi INSTANCE = new PlatformImpl();

    /** Currently running Agent Containers, mapping container ID to description */
    private final Map<String, AgentContainer> runningContainers = new HashMap<>();

    /** Currently connected other Runtime Platforms, mapping URL to description */
    private final Map<String, RuntimePlatform> connectedPlatforms = new HashMap<>();

    /** Set of remote Runtime Platform URLs with a pending connection request */
    private final Set<String> pendingConnections = new HashSet<>();


    private final DockerClient dockerClient = buildClient();

    private DockerClient buildClient() {

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

        return  DockerClientImpl.getInstance(dockerConfig, dockerHttpClient);
    }


    @Override
    public RuntimePlatform getInfo() {
        log.info("GET INFO");
        return new RuntimePlatform(
                "https://i-dont-really-know.com",
                List.copyOf(runningContainers.values()),
                List.of("nothing"),
                List.of()
        );
    }

    /*
     * AGENTS ROUTES
     */

    @Override
    public List<AgentDescription> getAgents() {
        log.info("GET AGENTS");
        return runningContainers.values().stream()
                .flatMap(c -> c.getAgents().stream())
                .collect(Collectors.toList());
    }

    @Override
    public AgentDescription getAgent(String agentId) {
        log.info(String.format("GET AGENT: %s", agentId));
        return runningContainers.values().stream()
                .flatMap(c -> c.getAgents().stream())
                .filter(a -> a.getAgentId().equals(agentId))
                .findAny().orElse(null);
    }

    @Override
    public void send(String agentId, Message message) throws IOException {
        log.info(String.format("SEND: %s, %s", agentId, message));
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
        log.info(String.format("BROADCAST: %s, %s", channel, message));
        for (AgentContainer container : runningContainers.values()) {
            getClient(container).post(String.format("/broadcast/%s", channel), message, null);
        }
        // TODO broadcast to other platforms... how to prevent infinite loops? optional flag parameter?
    }

    @Override
    public JsonNode invoke(String action, Map<String, JsonNode> parameters) throws IOException {
        log.info(String.format("INVOKE: %s, %s", action, parameters));
        var container = runningContainers.values().stream()
                .filter(c -> c.getAgents().stream()
                        .anyMatch(a -> a.getActions().stream()
                                .anyMatch(x -> x.getName().equals(action))))
                .findAny().orElse(null);
        if (container != null) {
            return getClient(container).post(String.format("/invoke/%s", action), parameters, JsonNode.class);
        } else {
            // TODO check connected platforms
        }
        return null;
    }

    @Override
    public JsonNode invoke(String agentId, String action, Map<String, JsonNode> parameters) throws IOException {
        log.info(String.format("INVOKE: %s, %s, %s", action, agentId, parameters));
        // TODO check that parameters match
        var container = runningContainers.values().stream()
                .filter(c -> c.getAgents().stream()
                        .anyMatch(a -> a.getAgentId().equals(agentId) &&
                                a.getActions().stream().anyMatch(x -> x.getName().equals(action))))
                .findAny().orElse(null);
        if (container != null) {
            return getClient(container).post(String.format("/invoke/%s/%s", action, agentId),
                    parameters, JsonNode.class);
        } else {
            // TODO check connected platforms
        }
        return null;
    }

    /*
     * CONTAINERS ROUTES
     */

    @Override
    public String addContainer(AgentContainerImage container) {
        System.out.println("ADD CONTAINER");
        System.out.println(container);
        // lookup container docker image
        // pull and start image
        // add container to internal containers table

        System.out.println("pulling...");

        dockerClient.pullImageCmd(container.getImageName()).start();

        System.out.println("creating...");

        CreateContainerResponse res = dockerClient.createContainerCmd(container.getImageName()).exec();

        System.out.println("res " + res);

        System.out.println("running...");

        dockerClient.startContainerCmd(res.getId()).exec();
        System.out.println("container id: " + res.getId());

        return res.getId();
    }

    @Override
    public List<AgentContainer> getContainers() {
        log.info("GET CONTAINERS");
        return List.copyOf(runningContainers.values());
    }

    @Override
    public AgentContainer getContainer(String containerId) {
        log.info(String.format("GET CONTAINER: %s", containerId));
        return runningContainers.values().stream()
                .filter(c -> c.getContainerId().equals(containerId))
                .findAny().orElse(null);
    }

    @Override
    public boolean removeContainer(String containerId) {
        log.info(String.format("REMOVE CONTAINER: %s", containerId));

        // TODO get container ID, use docker client to stop container...

        return false;
    }

    /*
     * CONNECTIONS ROUTES
     */

    @Override
    public boolean connectPlatform(String url) throws IOException {
        log.info(String.format("CONNECT PLATFORM: %s", url));
        // TODO add url to "pending" set of remote platforms
        //  call connect-platform route of remote platform at that URL
        //  if if returns as okay, finally add to connected platforms
        //  if url already in pending list, do nothing (might be callback from other platform)

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
        log.info("GET CONNECTIONS");
        return List.copyOf(connectedPlatforms.keySet());
    }

    @Override
    public boolean disconnectPlatform(String url) {
        log.info(String.format("DISCONNECT PLATFORM: %s", url));
        if (connectedPlatforms.containsKey(url)) {
            var client = new RestHelper(url);
            // TODO call "delete" on remote platform (to be implemented in REST Helper)
            return true;
        }
        return false;
    }

    /*
     * HELPER METHODS
     */

    private RestHelper getClient(AgentContainer container) {
        // TODO get URL to started Docker Container
        //  or extend this method to actually call the route?
        return new RestHelper("http://localhost:8082");
    }

}
