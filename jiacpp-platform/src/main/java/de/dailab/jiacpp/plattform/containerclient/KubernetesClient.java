package de.dailab.jiacpp.plattform.containerclient;

import com.github.dockerjava.api.exception.NotModifiedException;
import de.dailab.jiacpp.api.AgentContainerApi;
import de.dailab.jiacpp.model.AgentContainer;
import de.dailab.jiacpp.model.AgentContainerImage;
import de.dailab.jiacpp.plattform.PlatformConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.java.Log;
import java.util.AbstractMap;


import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.openapi.models.V1Secret;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.Set;
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
                String kubeConfigPath = System.getProperty("user.home") + config.kubernetesConfig;
                client = Config.fromConfig(kubeConfigPath);
            } else {
                throw new RuntimeException("Invalid platform environment: " + config.platformEnvironment);
            }
            Configuration.setDefaultApiClient(client);
            this.coreApi = new CoreV1Api();
        } catch (IOException e) {
            log.severe("IOException: " + e.getMessage());
        }

        this.config = config;
        this.auth = loadKubernetesSecrets();
        this.pods = new HashMap<>();
        this.usedPorts = new HashSet<>();

    }

    @Override
    public AgentContainer.Connectivity startContainer(String containerId, AgentContainerImage image) throws IOException, NoSuchElementException {
        
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
                            new V1EnvVar().name(AgentContainerApi.ENV_PLATFORM_URL).value(config.getOwnBaseUrl())
                        ))
                ))
                .imagePullSecrets(registrySecret == null ? null : Collections.singletonList(new V1LocalObjectReference().name(registrySecret)))
            );

        V1Pod createdPod = null;
        try {
            createdPod = coreApi.createNamespacedPod(config.kubernetesNamespace, pod, null, null, null);
            log.info("Pod created: " + createdPod.getMetadata().getName());
        } catch (ApiException e) {
            log.severe("Error creating pod: " + e.getMessage());
        }


        for (Map.Entry<Integer, Integer> entry : portMap.entrySet()) {
            int containerPort = entry.getKey();
            int hostPort = entry.getValue();
            String protocol = image.getExtraPorts().get(containerPort).getProtocol();
            V1Service service = createNodePortService(containerId, hostPort, containerPort, protocol);

            try {
                coreApi.createNamespacedService(config.kubernetesNamespace, service, null, null, null);
            } catch (ApiException e) {
                log.severe("Error creating service for port " + containerPort + ": " + e.getMessage());
                e.printStackTrace();
                log.severe("Response body: " + e.getResponseBody());
            }
        }



        String podIp = null;
        
        try {
            // Wait for the pod to be in the Running state and have an IP address assigned
            String podName = createdPod.getMetadata().getName();
            ApiClient apiClient = coreApi.getApiClient();
            Watch<V1Pod> watch = Watch.createWatch(
                apiClient,
                coreApi.listNamespacedPodCall(config.kubernetesNamespace, null, null, null, null, null, null, null, null, null, true, null),
                new TypeToken<Watch.Response<V1Pod>>(){}.getType());


            
            for (Watch.Response<V1Pod> item : watch) {
                if (item.object.getMetadata().getName().equals(podName) &&
                    item.object.getStatus().getPhase().equalsIgnoreCase("Running") &&
                    item.object.getStatus().getPodIP() != null) {
                    podIp = item.object.getStatus().getPodIP();
                    log.info("Pod IP: " + podIp);
                    break;
                }
            }
            watch.close();

        } catch (ApiException e) {
            log.severe("ApiException: " + e.getMessage());
        }

        String podUid = createdPod.getMetadata().getUid();

        var connectivity = new AgentContainer.Connectivity(
                config.getOwnBaseUrl().replaceAll(":\\d+$", ""),  // TODO change to remote docker host once implemented (#23)
                portMap.get(image.getApiPort()),
                extraPorts.keySet().stream().collect(Collectors.toMap(portMap::get, extraPorts::get))
        );

        pods.put(containerId, new PodInfo(podUid, podIp, connectivity));

        return connectivity;

    }


    @Override
    public void stopContainer(String containerId) throws IOException {
        try {
            // remove container info, stop container
            var containerInfo = pods.remove(containerId);
            try {
                coreApi.deleteNamespacedPod(containerId, config.kubernetesNamespace, null, null, null, null, null, null);
                log.info("Pod deleted: " + containerId);
            } catch (ApiException e) {
                log.severe("Error deleting pod: " + e.getMessage());
            }

            // free up ports used by this container
            // TODO do this first, or in finally?
            usedPorts.remove(containerInfo.connectivity.getApiPortMapping());
            usedPorts.removeAll(containerInfo.connectivity.getExtraPortMappings().keySet());
        } catch (NotModifiedException e) { // TODO docker exceptoin, can't occur here
            var msg = "Could not stop Container " + containerId + "; already stopped?";
            log.warning(msg);
            throw new NoSuchElementException(msg);
        }
    }

    @Override
    public String getIP(String podId) {
        return pods.get(podId).getInternalIp();
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
        if (config.registryNames.isEmpty()) {
            return Map.of();
        }
        var sep = config.registrySeparator;
        var registries = config.registryNames.split(sep);
        var logins = config.registryLogins.split(sep);
        var passwords = config.registryPasswords.split(sep);

        if (registries.length != logins.length || registries.length != passwords.length) {
            log.warning("Number of Registry Names does not match Login Usernames and Passwords");
            return Map.of();
        } else {
            return IntStream.range(0, registries.length)
                    .mapToObj(i -> createKubernetesSecret(registries[i], logins[i], passwords[i]))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }


    private Map.Entry<String, String> createKubernetesSecret(String registryAddress, String username, String password) {
        String secretName = registryAddress.replaceAll("[^a-zA-Z0-9-]", "-");

        String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));

        Map<String, byte[]> dockerConfigJson = new HashMap<>();
        dockerConfigJson.put(".dockerconfigjson", ("{\"auths\": {\"" + registryAddress + "\": {\"auth\": \"" + auth + "\"}}}").getBytes(StandardCharsets.UTF_8));

        V1Secret secret = new V1Secret()
                .metadata(new V1ObjectMeta().name(secretName).namespace(config.kubernetesNamespace))
                .type("kubernetes.io/dockerconfigjson")
                .data(dockerConfigJson);

        try {
            this.coreApi.createNamespacedSecret(config.kubernetesNamespace, secret, null, null, null);
        } catch (ApiException e) {
            log.severe("Exception when creating secret: " + e.getResponseBody());
        }

        return new AbstractMap.SimpleEntry<>(registryAddress, secretName);
    }

}
