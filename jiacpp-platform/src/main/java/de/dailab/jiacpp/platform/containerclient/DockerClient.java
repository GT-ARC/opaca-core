package de.dailab.jiacpp.platform.containerclient;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.google.common.base.Strings;
import de.dailab.jiacpp.api.AgentContainerApi;
import de.dailab.jiacpp.model.AgentContainer;
import de.dailab.jiacpp.model.AgentContainerImage;
import de.dailab.jiacpp.platform.PlatformConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.java.Log;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Container Client for running Agent Containers in Docker, possibly on a remote host.
 *
 * Some documentation:
 * - https://github.com/docker-java/docker-java/blob/master/docs/getting_started.md
 * - https://www.baeldung.com/docker-java-api
 */
@Log
public class DockerClient implements ContainerClient {

    private PlatformConfig config;

    /** Client for accessing (remote) Docker runtime */
    private com.github.dockerjava.api.DockerClient dockerClient;

    /** additional Docker-specific information on agent containers */
    private Map<String, DockerContainerInfo> dockerContainers;

    /** Available Docker Auth */
    private Map<String, AuthConfig> auth;

    /** Set of already used ports on target Docker host */
    private Set<Integer> usedPorts;

    @Data
    @AllArgsConstructor
    static class DockerContainerInfo {
        String containerId;
        AgentContainer.Connectivity connectivity;
    }

    @Override
    public void initialize(PlatformConfig config) {
        this.config = config;

        DockerClientConfig dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(getDockerHost())
                .build();

        DockerHttpClient dockerHttpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(dockerConfig.getDockerHost())
                .sslConfig(dockerConfig.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        this.auth = loadDockerAuth();
        this.dockerClient = DockerClientImpl.getInstance(dockerConfig, dockerHttpClient);
        this.dockerContainers = new HashMap<>();
        this.usedPorts = new HashSet<>();
    }

    @Override
    public AgentContainer.Connectivity startContainer(String containerId, String token, AgentContainerImage image) throws IOException, NoSuchElementException {
        
        var imageName = image.getImageName();
        var extraPorts = image.getExtraPorts();
        try {
            if (! isImagePresent(imageName)) {
                pullDockerImage(imageName);
            }

            // port mappings
            Map<Integer, Integer> portMap = Stream.concat(Stream.of(image.getApiPort()), extraPorts.keySet().stream())
                    .collect(Collectors.toMap(p -> p, this::reserveNextFreePort));

            log.info("Creating Container...");
            CreateContainerResponse res = dockerClient.createContainerCmd(imageName)
                    .withEnv(
                            String.format("%s=%s", AgentContainerApi.ENV_CONTAINER_ID, containerId),
                            String.format("%s=%s", AgentContainerApi.ENV_TOKEN, token),
                            String.format("%s=%s", AgentContainerApi.ENV_PLATFORM_URL, config.getOwnBaseUrl()))
                    .withHostConfig(HostConfig.newHostConfig()
                            .withPortBindings(portMap.entrySet().stream().map(
                                    e -> PortBinding.parse(String.format("%s:%s", e.getValue(), e.getKey()))
                            ).collect(Collectors.toList()))
                    )
                    .withExposedPorts(portMap.keySet().stream().map(ExposedPort::tcp).collect(Collectors.toList()))
                    .exec();
            log.info(String.format("Result: %s", res));

            log.info("Starting Container...");
            dockerClient.startContainerCmd(res.getId()).exec();

            // create connectivity object
            var connectivity = new AgentContainer.Connectivity(
                    getContainerBaseUrl(),
                    portMap.get(image.getApiPort()),
                    extraPorts.keySet().stream().collect(Collectors.toMap(portMap::get, extraPorts::get))
            );
            dockerContainers.put(containerId, new DockerContainerInfo(res.getId(), connectivity));

            return connectivity;

        } catch (NotFoundException e) {
            // might theoretically happen if image is deleted between pull and run...
            log.warning("Image not found: " + imageName);
            throw new NoSuchElementException("Image not found: " + imageName);
        }
    }

    @Override
    public void stopContainer(String containerId) throws IOException {
        try {
            // remove container info, stop container
            var containerInfo = dockerContainers.remove(containerId);
            dockerClient.stopContainerCmd(containerInfo.containerId).exec();
            // free up ports used by this container
            // TODO do this first, or in finally?
            usedPorts.remove(containerInfo.connectivity.getApiPortMapping());
            usedPorts.removeAll(containerInfo.connectivity.getExtraPortMappings().keySet());
        } catch (NotModifiedException e) {
            var msg = "Could not stop Container " + containerId + "; already stopped?";
            log.warning(msg);
            throw new NoSuchElementException(msg);
        }
        // TODO possibly that the container refuses being stopped? call "kill" instead? how to test this?
    }

    @Override
    public String getUrl(String containerId) {
        var conn = dockerContainers.get(containerId).connectivity;
        return conn.getPublicUrl() + ":" + conn.getApiPortMapping();
    }

    private String getDockerHost() {
        return Strings.isNullOrEmpty(config.remoteDockerHost)
                ? "unix:///var/run/docker.sock"
                : String.format("tcp://%s:%s", config.remoteDockerHost, config.remoteDockerPort);
    }

    private String getContainerBaseUrl() {
        return Strings.isNullOrEmpty(config.remoteDockerHost)
                ? config.getOwnBaseUrl().replaceAll(":\\d+$", "")
                : String.format("http://%s", config.remoteDockerHost);
    }

    /**
     * Try to pull the Docker image from registry where it can be found (according to image name).
     * Raise NoSuchElementException if image can not be pulled for whatever reason.
     */
    private void pullDockerImage(String imageName) {
        log.info("Pulling Image..." + imageName);
        try {
            var registry = imageName.split("/")[0];
            dockerClient.pullImageCmd(imageName)
                    .withAuthConfig(this.auth.get(registry))
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();
        } catch (InterruptedException e) {
            log.warning(e.getMessage());
        } catch (InternalServerErrorException e) {
            log.severe("Pull Image failed: " + e.getMessage());
            throw new NoSuchElementException("Failed to Pull image: " + e.getMessage());
        }
    }

    /**
     * Starting from the given preferred port, get and reserve the next free port.
     */
    private int reserveNextFreePort(int port) {
        while (usedPorts.contains(port)) {
            // TODO how to handle ports blocked by other containers or applications? just ping ports?
            port++;
        }
        usedPorts.add(port);
        return port;
    }

    private boolean isImagePresent(String imageName) {
        try {
            this.dockerClient.inspectImageCmd(imageName).exec();
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }
    
    private Map<String, AuthConfig> loadDockerAuth() {
        return config.loadDockerAuth().stream().collect(Collectors.toMap(
                PlatformConfig.ImageRegistryAuth::getRegistry,
                x -> new AuthConfig()
                        .withRegistryAddress(x.getRegistry())
                        .withUsername(x.getLogin())
                        .withPassword(x.getLogin())));
    }
}
