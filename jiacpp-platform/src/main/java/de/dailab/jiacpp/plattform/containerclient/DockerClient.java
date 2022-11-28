package de.dailab.jiacpp.plattform.containerclient;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import de.dailab.jiacpp.api.AgentContainerApi;
import de.dailab.jiacpp.plattform.PlatformConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.java.Log;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

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

    @Data
    @AllArgsConstructor
    static class DockerContainerInfo {
        String containerId;
        String internalIp;
    }

    @Override
    public void initialize(PlatformConfig config) {

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

        this.config = config;
        this.dockerClient = DockerClientImpl.getInstance(dockerConfig, dockerHttpClient);
        this.dockerContainers = new HashMap<>();
    }

    @Override
    public void startContainer(String containerId, String imageName) throws IOException, NoSuchElementException {
        try {
            // pull image if not present
            if (dockerClient.listImagesCmd().withImageNameFilter(imageName).exec().isEmpty()) {
                log.info("Pulling Image..." + imageName);
                try {
                    dockerClient.pullImageCmd(imageName).exec(new PullImageResultCallback())
                            .awaitCompletion();
                } catch (InterruptedException e) {
                    log.warning(e.getMessage());
                }
            }

            log.info("Creating Container...");
            CreateContainerResponse res = dockerClient.createContainerCmd(imageName)
                    .withEnv(
                            String.format("%s=%s", AgentContainerApi.ENV_CONTAINER_ID, containerId),
                            String.format("%s=%s", AgentContainerApi.ENV_PLATFORM_URL, config.getOwnBaseUrl()))
                    .exec();
            log.info(String.format("Result: %s", res));

            log.info("Starting Container...");
            dockerClient.startContainerCmd(res.getId()).exec();

            // TODO get internal IP... why is this deprecated?
            InspectContainerResponse info = dockerClient.inspectContainerCmd(res.getId()).exec();
            dockerContainers.put(containerId, new DockerContainerInfo(res.getId(), info.getNetworkSettings().getIpAddress()));

        } catch (NotFoundException e) {
            log.warning("Image not found: " + imageName);
            throw new NoSuchElementException("Image not found: " + imageName);
        }
    }

    @Override
    public void stopContainer(String containerId) throws IOException {
        var dockerId = dockerContainers.get(containerId).getContainerId();
        dockerClient.stopContainerCmd(dockerId).exec();
        // TODO get result, check if and when it is finally stopped?
        // TODO handle case of container already being terminated (due to error or similar)
        // TODO possibly that the container refuses being stopped? call "kill" instead? how to test this?
    }

    @Override
    public String getIP(String containerId) {
        return dockerContainers.get(containerId).getInternalIp();
    }

}
