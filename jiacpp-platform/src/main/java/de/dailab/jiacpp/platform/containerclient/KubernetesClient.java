package de.dailab.jiacpp.platform.containerclient;

import de.dailab.jiacpp.api.AgentContainerApi;
import de.dailab.jiacpp.model.AgentContainer;
import de.dailab.jiacpp.model.AgentContainerImage;
import de.dailab.jiacpp.platform.PlatformConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.java.Log;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.Set;
import java.util.List;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Collections;
import com.google.gson.reflect.TypeToken;

/**
 * Container Client for running Agent Containers in Kubernetes.
 */
@Log
public class KubernetesClient implements ContainerClient {

    private PlatformConfig config;
    private CoreV1Api coreApi;
    private String namespace;

    /** additional Kubernetes-specific information on agent containers */
    private Map<String, PodInfo> pods;

    /** Available Docker Auth */
    private Map<String, String> auth;

    /** Set of already used ports on target Kubernetes host */
    private Set<Integer> usedPorts;

    @Data
    @AllArgsConstructor
    static class PodInfo {
        String containerId;
        String internalIp;
        AgentContainer.Connectivity connectivity;
    }

    @Override
    public void initialize(PlatformConfig config) {
        // Initialize the Kubernetes API client
        try {
            ApiClient client;
            if (config.platformEnvironment == PlatformConfig.PlatformEnvironment.KUBERNETES) {
                // If running inside a pod, it will use the default service account
                client = Config.defaultClient();
            } else if (config.platformEnvironment == PlatformConfig.PlatformEnvironment.NATIVE) {
                // If running locally, it will use the default kubeconfig file location
                var configPath = config.kubernetesConfig.replaceAll("^~", System.getProperty("user.home"));
                client = Config.fromConfig(configPath);
            } else {
                throw new RuntimeException("Invalid platform environment: " + config.platformEnvironment);
            }
            Configuration.setDefaultApiClient(client);
            this.coreApi = new CoreV1Api();
        } catch (IOException e) {
            log.severe("Could not initialize Kubernetes Client: " + e.getMessage());
            throw new RuntimeException(e);
        }

        this.namespace = config.kubernetesNamespace;
        this.config = config;
        this.auth = loadKubernetesSecrets();
        this.pods = new HashMap<>();
        this.usedPorts = new HashSet<>();
    }

    @Override
    public void testConnectivity() {
        try {
            this.coreApi.listNamespacedPod(this.namespace, null, null, null, null, null, null, null, null, null, null);
        } catch (ApiException e) {
            throw new RuntimeException("Could not initialize Kubernetes Client", e);
        }
    }

    @Override
    public AgentContainer.Connectivity startContainer(String containerId, String token, AgentContainerImage image) throws IOException, NoSuchElementException {
        
        var imageName = image.getImageName();
        var registry = imageName.split("/")[0];
        String registrySecret = this.auth.get(registry);
        var extraPorts = image.getExtraPorts();

        Map<Integer, Integer> portMap = extraPorts.keySet().stream()
                .collect(Collectors.toMap(p -> p, this::reserveNextFreePort));

        V1Pod pod = new V1Pod()
            .metadata(new V1ObjectMeta().name(containerId).labels(Collections.singletonMap("app", containerId)))
            .spec(new V1PodSpec()
                .containers(Collections.singletonList(
                    new V1Container()
                        .name(containerId)
                        .image(imageName)
                        .ports(Collections.singletonList(
                            new V1ContainerPort().containerPort(image.getApiPort())
                        ))
                        .env(Arrays.asList(
                            new V1EnvVar().name(AgentContainerApi.ENV_CONTAINER_ID).value(containerId),
                            new V1EnvVar().name(AgentContainerApi.ENV_TOKEN).value(token),
                            new V1EnvVar().name(AgentContainerApi.ENV_PLATFORM_URL).value(config.getOwnBaseUrl())
                        ))
                ))
                .imagePullSecrets(registrySecret == null ? null : Collections.singletonList(new V1LocalObjectReference().name(registrySecret)))
            );

        try {
            V1Pod createdPod = coreApi.createNamespacedPod(namespace, pod, null, null, null);
            log.info("Pod created: " + createdPod.getMetadata().getName());

            createServicesForPorts(containerId, image, portMap);

            String podIp = waitForPodIP(createdPod.getMetadata().getName());
            log.info("Pod IP: " + podIp);

            String podUid = createdPod.getMetadata().getUid();

            var connectivity = new AgentContainer.Connectivity(
                    config.getOwnBaseUrl().replaceAll(":\\d+$", ""),  // TODO is this correct?
                    portMap.get(image.getApiPort()),
                    extraPorts.keySet().stream().collect(Collectors.toMap(portMap::get, extraPorts::get))
            );

            pods.put(containerId, new PodInfo(podUid, podIp, connectivity));

            return connectivity;
        } catch (ApiException e) {
            log.severe("Error creating pod: " + e.getMessage());
            throw new IOException("Failed to create Pod: " + e.getMessage());
        }
    }


    @Override
    public void stopContainer(String containerId) throws IOException {
        try {
            // remove container info, stop container
            var containerInfo = pods.remove(containerId);
            coreApi.deleteNamespacedPod(containerId, namespace, null, null, null, null, null, null);
            // free up ports used by this container
            // TODO do this first, or in finally?
            usedPorts.remove(containerInfo.connectivity.getApiPortMapping());
            usedPorts.removeAll(containerInfo.connectivity.getExtraPortMappings().keySet());
        } catch (ApiException e) {
            var msg = "Could not stop Container " + containerId + "; already stopped?";
            log.warning(msg);
            throw new NoSuchElementException(msg);
        }
    }

    @Override
    public String getUrl(String podId) {
        var ip = pods.get(podId).getInternalIp();
        return String.format("http://%s:%s", ip, AgentContainerApi.DEFAULT_PORT);
    }


    /**
     * Wait for the pod to be in the Running state and have an IP address assigned
     */
    private String waitForPodIP(String podName) throws ApiException, IOException, NoSuchElementException {
        ApiClient apiClient = coreApi.getApiClient();
        try (Watch<V1Pod> watch = Watch.createWatch(
                apiClient,
                coreApi.listNamespacedPodCall(namespace, null, null, null, null, null, null, null, null, null, true, null),
                new TypeToken<Watch.Response<V1Pod>>(){}.getType())) {

            for (Watch.Response<V1Pod> item : watch) {
                V1Pod pod = item.object;
                String name = pod.getMetadata().getName();
                String phase = pod.getStatus().getPhase();
                List<V1ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();

                if (name.equals(podName)) {
                    if (phase.equalsIgnoreCase("Running") && pod.getStatus().getPodIP() != null) {
                        return pod.getStatus().getPodIP();
                    } else if (containerStatuses != null) {
                        for (V1ContainerStatus status : containerStatuses) {
                            V1ContainerState state = status.getState();
                            if (state.getWaiting() != null && "ImagePullBackOff".equals(state.getWaiting().getReason())) {
                                coreApi.deleteNamespacedPod(podName, namespace, null, null, null, null, null, null);
                                throw new NoSuchElementException("Container image could not be pulled");
                            }
                        }
                    }
                }
            }
        }
        throw new IOException("Container did not start");
    }



    private void createServicesForPorts(String containerId, AgentContainerImage image, Map<Integer, Integer> portMap) throws ApiException {
        for (Map.Entry<Integer, Integer> entry : portMap.entrySet()) {
            int containerPort = entry.getKey();
            int hostPort = entry.getValue();
            String protocol = image.getExtraPorts().get(containerPort).getProtocol();
            V1Service service = createNodePortService(containerId, hostPort, containerPort, protocol);

            // could not expose port as service -> could not create pod
            coreApi.createNamespacedService(namespace, service, null, null, null);
        }
    }

    private V1Service createNodePortService(String containerId, int port, int targetPort, String protocol) {
        String serviceName = "svc-" + containerId + "-" + port;
        return new V1Service()
            .metadata(new V1ObjectMeta().name(serviceName).labels(Collections.singletonMap("app", containerId)))
            .spec(new V1ServiceSpec()
                .type("NodePort")
                .selector(Collections.singletonMap("app", containerId))
                .ports(Collections.singletonList(
                    new V1ServicePort()
                        .port(port)
                        .targetPort(new IntOrString(targetPort))
                        .nodePort(port) 
                        .protocol(protocol)
                ))
            );
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

    private Map<String, String> loadKubernetesSecrets() {
        return config.loadDockerAuth().stream().collect(Collectors.toMap(
                PlatformConfig.ImageRegistryAuth::getRegistry,
                x -> createKubernetesSecret(x.getRegistry(), x.getLogin(), x.getPassword())));
    }

    private String createKubernetesSecret(String registryAddress, String username, String password) {
        String secretName = registryAddress.replaceAll("[^a-zA-Z0-9-]", "-");

        String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        String authJson = "{\"auths\": {\"" + registryAddress + "\": {\"auth\": \"" + auth + "\"}}}";

        V1Secret secret = new V1Secret()
                .metadata(new V1ObjectMeta().name(secretName).namespace(namespace))
                .type("kubernetes.io/dockerconfigjson")
                .data(Map.of(".dockerconfigjson", authJson.getBytes(StandardCharsets.UTF_8)));

        try {
            this.coreApi.createNamespacedSecret(namespace, secret, null, null, null);
        } catch (ApiException e) {
            log.severe("Exception when creating secret: " + e.getResponseBody());
        }

        return secretName;
    }

}
