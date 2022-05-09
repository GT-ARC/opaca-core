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
import lombok.Builder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class provides the actual implementation of the API routes. Might also be split up
 * further, e.g. for agent-forwarding, container-management, and linking to other platforms.
 */
public class PlatformImpl implements RuntimePlatformApi {

    public static final RuntimePlatformApi INSTANCE = new PlatformImpl();

    /** Currently running Agent Containers, mapping container ID to description */
    private final Map<String, AgentContainer> runningContainers = new HashMap<>();


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
        System.out.println("GET INFO");
        // TODO how to get own base URL? own "provides" value?
        //  separate PlatformConfig analogous to AgentContainerImage class?
        return new RuntimePlatform(
                "https://i-dont-really-know.com",
                new ArrayList<>(runningContainers.values()),
                List.of("nothing"),
                List.of()
        );
    }

    /*
     * AGENTS ROUTES
     */

    @Override
    public List<AgentDescription> getAgents() {
        System.out.println("GET AGENTS");
        return runningContainers.values().stream()
                .flatMap(c -> c.getAgents().stream())
                .collect(Collectors.toList());
    }

    @Override
    public AgentDescription getAgent(String agentId) {
        System.out.println("GET AGENT");
        System.out.println(agentId);
        return runningContainers.values().stream()
                .flatMap(c -> c.getAgents().stream())
                .filter(a -> a.getAgentId().equals(agentId))
                .findAny().orElse(null);
    }

    @Override
    public void send(String agentId, Message message) {
        System.out.println("SEND");
        System.out.println(agentId);
        System.out.println(message);
        // TODO get agent container this agent is running in; forward REST call to container
    }

    @Override
    public void broadcast(String channel, Message message) {
        System.out.println("BROADCAST");
        System.out.println(channel);
        System.out.println(message);
        // TODO iterate containers, forward REST call to all containers
    }

    @Override
    public JsonNode invoke(String action, Map<String, JsonNode> parameters) {
        System.out.println("INVOKE");
        System.out.println(action);
        System.out.println(parameters);
        // TODO find (any) container providing this action, forward REST call to that container
        return null;
    }

    @Override
    public JsonNode invoke(String agentId, String action, Map<String, JsonNode> parameters) {
        System.out.println("INVOKE");
        System.out.println(action);
        System.out.println(agentId);
        System.out.println(parameters);
        // TODO find container with that agent, forward REST call to container
        // lookup registered containers for container including agent with agentid
        // check that this agent has action "action"
        // check that parameters match
        // invoke same REST Route on Container with that agent
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
        System.out.println("GET CONTAINERS");
        return null;
    }

    @Override
    public AgentContainer getContainer(String containerId) {
        System.out.println("GET CONTAINER");
        System.out.println(containerId);
        return null;
    }

    @Override
    public boolean removeContainer(String containerId) {
        System.out.println("REMOVE CONTAINER");
        System.out.println(containerId);
        return false;
    }

    /*
     * CONNECTIONS ROUTES
     */

    @Override
    public boolean connectPlatform(String url) {
        System.out.println("CONNECT PLATFORM");
        System.out.println(url);
        // TODO add url to "pending" set of remote platforms
        //  call connect-platform route of remote platform at that URL
        //  if if returns as okay, finally add to connected platforms
        //  if url already in pending list, do nothing (might be callback from other platform)
        return false;
    }

    @Override
    public List<String> getConnections() {
        System.out.println("GET CONNECTIONS");
        return null;
    }

    @Override
    public boolean disconnectPlatform(String url) {
        System.out.println("DISCONNECT PLATFORM");
        System.out.println(url);
        // TODO similar to connect, forwarding to other platform, but then disconnect
        return false;
    }
}
