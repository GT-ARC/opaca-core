package de.dailab.jiacpp.platform.containerclient;

import de.dailab.jiacpp.api.AgentContainerApi;
import de.dailab.jiacpp.model.AgentContainer;
import de.dailab.jiacpp.model.AgentContainerImage;
import de.dailab.jiacpp.platform.PlatformConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.java.Log;
import java.util.AbstractMap;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
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
import java.util.stream.IntStream;
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
    private AppsV1Api appsApi;
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
            this.appsApi = new AppsV1Api();
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
    public AgentContainer.Connectivity startContainer(String containerId, String token, AgentContainerImage image) throws IOException, NoSuchElementException {
        
        var imageName = image.getImageName();
        var registry = imageName.split("/")[0];
        String registrySecret = this.auth.get(registry);
        var extraPorts = image.getExtraPorts();

        Map<Integer, Integer> portMap = extraPorts.keySet().stream()
                .collect(Collectors.toMap(p -> p, this::reserveNextFreePort));


        V1Deployment deployment = new V1Deployment()
                .metadata(new V1ObjectMeta().name(containerId))
                .spec(new V1DeploymentSpec()
	        	.strategy(new V1DeploymentStrategy()
	            		.rollingUpdate(new V1RollingUpdateDeployment()
	                	.maxSurge(new IntOrString(1))
	                	.maxUnavailable(new IntOrString(0))
	            		)
	           	 	.type("RollingUpdate")
	                 )
                        .replicas(1)
                        .selector(new V1LabelSelector().matchLabels(Collections.singletonMap("app", containerId)))
                        .template(new V1PodTemplateSpec()
                                .metadata(new V1ObjectMeta().labels(Collections.singletonMap("app", containerId)))
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
                                )));

        String serviceId = "svc-" + containerId;
        V1Service service = new V1Service()
                .metadata(new V1ObjectMeta().name(serviceId))
                .spec(new V1ServiceSpec()
                        .selector(Collections.singletonMap("app", containerId))
                        .ports(Collections.singletonList(
                                new V1ServicePort().port(image.getApiPort()).targetPort(new IntOrString(image.getApiPort()))
                        ))
                        .type("ClusterIP"));


        try {
            V1Deployment createdDeployment = appsApi.createNamespacedDeployment(namespace, deployment, null, null, null);
            System.out.println("Deployment created: " + createdDeployment.getMetadata().getName());
            
            V1Service createdService = coreApi.createNamespacedService(namespace, service, null, null, null);
            System.out.println("Service created: " + createdService.getMetadata().getName());
            String serviceIP = createdService.getSpec().getClusterIP();

            createServicesForPorts(containerId, image, portMap);

            log.info("Deployment IP: " + serviceIP);

            var connectivity = new AgentContainer.Connectivity(
                    config.getOwnBaseUrl().replaceAll(":\\d+$", ""),  // TODO is this correct?
                    portMap.get(image.getApiPort()),
                    extraPorts.keySet().stream().collect(Collectors.toMap(portMap::get, extraPorts::get))
            );

            pods.put(containerId, new PodInfo(createdDeployment.getMetadata().getName(), serviceIP, connectivity));

            return connectivity;
        } catch (ApiException e) {
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
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

        Map<String, byte[]> dockerConfigJson = Map.of(".dockerconfigjson", ("{\"auths\": {\"" + registryAddress + "\": {\"auth\": \"" + auth + "\"}}}").getBytes(StandardCharsets.UTF_8));

        V1Secret secret = new V1Secret()
                .metadata(new V1ObjectMeta().name(secretName).namespace(namespace))
                .type("kubernetes.io/dockerconfigjson")
                .data(dockerConfigJson);

        try {
            this.coreApi.createNamespacedSecret(namespace, secret, null, null, null);
        } catch (ApiException e) {
            log.severe("Exception when creating secret: " + e.getResponseBody());
        }

        return new AbstractMap.SimpleEntry<>(registryAddress, secretName);
    }

}
