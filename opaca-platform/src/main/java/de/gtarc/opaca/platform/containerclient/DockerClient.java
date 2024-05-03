package de.gtarc.opaca.platform.containerclient;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.DockerException;
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
import de.gtarc.opaca.model.AgentContainer;
import de.gtarc.opaca.model.AgentContainerImage;
import de.gtarc.opaca.model.AgentContainerImage.ImageParameter;
import de.gtarc.opaca.model.PostAgentContainer;
import de.gtarc.opaca.platform.PlatformConfig;
import de.gtarc.opaca.platform.session.SessionData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.java.Log;
import org.apache.commons.lang3.SystemUtils;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
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
    public static class DockerContainerInfo {
        String containerId;
        AgentContainer.Connectivity connectivity;
    }

    @Override
    public void initialize(PlatformConfig config, SessionData sessionData) {
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
        this.dockerContainers = sessionData.dockerContainers;
        this.usedPorts = sessionData.usedPorts;
    }

    @Override
    public void testConnectivity() {
        try {
            this.dockerClient.listContainersCmd().exec();
        } catch (Exception e) {
            log.severe("Could not initialize Docker Client: " + e.getMessage());
            throw new RuntimeException("Could not initialize Docker Client", e);
        }
    }

    @Override
    public AgentContainer.Connectivity startContainer(String containerId, String token, String owner, PostAgentContainer container) throws IOException, NoSuchElementException {
        var image = container.getImage();
        var imageName = image.getImageName();
        var extraPorts = image.getExtraPorts();

        try {
            if (! isImagePresent(imageName)) {
                pullDockerImage(imageName);
            }

            // port mappings for API- and Extra-Ports
            var newPorts = new HashSet<Integer>();
            Map<Integer, Integer> portMap = Stream.concat(Stream.of(image.getApiPort()), extraPorts.keySet().stream())
                    .collect(Collectors.toMap(p -> p, p -> reserveNextFreePort(p, newPorts)));
            // translate to Docker PortBindings (incl. ExposedPort descriptions)
            List<PortBinding> portBindings = portMap.entrySet().stream()
                    .map(e -> PortBinding.parse(e.getValue() + ":" + e.getKey() + "/" + getProtocol(e.getKey(), image)))
                    .collect(Collectors.toList());

            log.info("Creating Container...");
            CreateContainerResponse res = dockerClient.createContainerCmd(imageName)
                    .withEnv(buildEnv(containerId, token, owner, image.getParameters(), container.getArguments()))
                    .withHostConfig(HostConfig.newHostConfig().withPortBindings(portBindings))
                    .withExposedPorts(portBindings.stream().map(PortBinding::getExposedPort).collect(Collectors.toList()))
                    .exec();

            log.info(String.format("Result: %s", res));

            log.info("Starting Container...");
            dockerClient.startContainerCmd(res.getId()).exec();

            var connectivity = new AgentContainer.Connectivity(
                    getContainerBaseUrl(),
                    portMap.get(image.getApiPort()),
                    extraPorts.keySet().stream().collect(Collectors.toMap(portMap::get, extraPorts::get))
            );
            dockerContainers.put(containerId, new DockerContainerInfo(res.getId(), connectivity));
            usedPorts.addAll(newPorts);

            return connectivity;

        } catch (NotFoundException e) {
            // might theoretically happen if image is deleted between pull and run...
            log.warning("Image not found: " + imageName);
            throw new NoSuchElementException("Image not found: " + imageName);
        } catch (DockerException e) {
            throw new IOException("Failed to start Docker container.", e);
        }
    }

    private String[] buildEnv(String containerId, String token, String owner, List<ImageParameter> parameters, Map<String, String> arguments) {
        return config.buildContainerEnv(containerId, token, owner, parameters, arguments).entrySet().stream()
                .map(e -> String.format("%s=%s", e.getKey(), e.getValue()))
                .toArray(String[]::new);
    }

    @Override
    public void stopContainer(String containerId) throws IOException {
        try {
            var containerInfo = dockerContainers.remove(containerId);
            usedPorts.remove(containerInfo.connectivity.getApiPortMapping());
            usedPorts.removeAll(containerInfo.connectivity.getExtraPortMappings().keySet());
            dockerClient.stopContainerCmd(containerInfo.containerId).exec();
        } catch (NotModifiedException e) {
            var msg = "Could not stop Container " + containerId + "; already stopped?";
            log.warning(msg);
            throw new NoSuchElementException(msg);
        }
        // TODO possibly that the container refuses being stopped? call "kill" instead? how to test this?
    }

    @Override
    public boolean isContainerAlive(String containerId) throws IOException {
        try {
            var containerInfo = dockerContainers.get(containerId);
            var res = dockerClient.inspectContainerCmd(containerInfo.containerId).exec();
            return res.getState().getRunning();
        } catch (NotFoundException e) {
            return false;
        }
    }

    @Override
    public String getUrl(String containerId) {
        var conn = dockerContainers.get(containerId).connectivity;
        return conn.getPublicUrl() + ":" + conn.getApiPortMapping();
    }

    private String getProtocol(int port, AgentContainerImage image) {
        if (image.getExtraPorts().containsKey(port)) {
            String protocol = image.getExtraPorts().get(port).getProtocol();
            return "udp".equalsIgnoreCase(protocol) ? "udp" : "tcp";
        } else {
            return "tcp";
        }
    }

    private String getLocalDockerHost() {
        // Just differentiates between Windows and others for now
        return SystemUtils.IS_OS_WINDOWS
                ? "npipe:////./pipe/docker_engine"
                : "unix:///var/run/docker.sock";
    }

    private String getDockerHost() {
        return Strings.isNullOrEmpty(config.remoteDockerHost)
                ? getLocalDockerHost()
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
    private int reserveNextFreePort(int port, Set<Integer> newPorts) {
        while (!isPortAvailable(port, newPorts)) ++port;
        newPorts.add(port);
        return port;
    }

    private boolean isPortAvailable(int port, Set<Integer> newPorts) {
        if (usedPorts.contains(port) || newPorts.contains(port)) return false;
        try (var s1 = new ServerSocket(port); var s2 = new DatagramSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
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
