package de.gtarc.opaca.platform.containerclient;

import de.gtarc.opaca.api.AgentContainerApi;
import de.gtarc.opaca.model.AgentContainer;
import de.gtarc.opaca.model.AgentContainerImage;
import de.gtarc.opaca.model.AgentContainerImage.ImageParameter;
import de.gtarc.opaca.model.PostAgentContainer;
import de.gtarc.opaca.platform.PlatformConfig;
import de.gtarc.opaca.platform.session.SessionData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.java.Log;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.util.Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Container Client for running Agent Containers in Kubernetes.
 */
@Log
public class KubernetesClient extends AbstractContainerClient {

    private PlatformConfig config;
    private CoreV1Api coreApi;
    private AppsV1Api appsApi;
    private String namespace;

    /** additional Kubernetes-specific information on agent containers */
    private Map<String, PodInfo> pods;

    /** Available Docker Auth */
    private Map<String, String> auth;


    @Data
    @AllArgsConstructor
    public static class PodInfo {
        String containerId;
        String internalIp;
        AgentContainer.Connectivity connectivity;
    }

    @Override
    public void initialize(PlatformConfig config, SessionData sessionData) {
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
        this.pods = sessionData.pods;
        this.usedPorts = sessionData.usedPorts;
    }

    @Override
    public void testConnectivity() {
        try {
            this.coreApi.listNamespacedPod(this.namespace).execute();
        } catch (ApiException e) {
            log.severe("Could not initialize Kubernetes Client: " + e.getMessage());
            throw new RuntimeException("Could not initialize Kubernetes Client", e);
        }
    }

    @Override
    public AgentContainer.Connectivity startContainer(String containerId, String token, String owner, PostAgentContainer container) throws IOException, NoSuchElementException {
        var image = container.getImage();
        var imageName = image.getImageName();
        var registry = imageName.split("/")[0];
        String registrySecret = this.auth.get(registry);
        var extraPorts = image.getExtraPorts();

        var newPorts = new HashSet<Integer>();
        Map<Integer, Integer> portMap = Stream.concat(Stream.of(image.getApiPort()), extraPorts.keySet().stream())
                .collect(Collectors.toMap(p -> p, p -> reserveNextFreePort(p, newPorts)));

        V1PodSpec podSpec = new V1PodSpec()
                        .containers(List.of(
                                new V1Container()
                                        .name(containerId)
                                        .image(imageName)
                                        .imagePullPolicy(config.alwaysPullImages ? "Always" : "IfNotPresent")
                                        .ports(List.of(
                                                new V1ContainerPort().containerPort(image.getApiPort())
                                        ))
                                        .env(buildEnv(containerId, token, owner, image.getParameters(), container.getArguments()))
                        ))
                        .imagePullSecrets(registrySecret == null ? null : List.of(new V1LocalObjectReference().name(registrySecret)))
                ;

        if (container.getClientConfig() instanceof PostAgentContainer.KubernetesConfig k8sConf) {
            // the default seems to be null anyway, so no null-check needed?
            podSpec.hostNetwork(k8sConf.getHostNetwork());
            podSpec.nodeName(k8sConf.getNodeName());
        }
        
        V1PodTemplateSpec podTemplateSpec = new V1PodTemplateSpec()
                .metadata(new V1ObjectMeta().labels(Map.of("app", containerId)))
                .spec(podSpec);

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
                        .selector(new V1LabelSelector().matchLabels(Map.of("app", containerId)))
                        .template(podTemplateSpec)
                );

        V1Service service = new V1Service()
                .metadata(new V1ObjectMeta().name(serviceId(containerId)))
                .spec(new V1ServiceSpec()
                        .selector(Map.of("app", containerId))
                        .ports(List.of(
                                new V1ServicePort().port(image.getApiPort()).targetPort(new IntOrString(image.getApiPort()))
                        ))
                        .type("ClusterIP"));

        try {
            V1Deployment createdDeployment = appsApi.createNamespacedDeployment(namespace, deployment).execute();
            log.info("Deployment created: " + createdDeployment.getMetadata().getName());
            
            V1Service createdService = coreApi.createNamespacedService(namespace, service).execute();
            log.info("Service created: " + createdService.getMetadata().getName());
            String serviceIP = createdService.getSpec().getClusterIP();
            log.info("Deployment IP: " + serviceIP);

            createServicesForPorts(containerId, image, portMap);

            var connectivity = new AgentContainer.Connectivity(
                    getContainerBaseUrl(),
                    portMap.get(image.getApiPort()),
                    extraPorts.keySet().stream().collect(Collectors.toMap(portMap::get, extraPorts::get))
            );

            pods.put(containerId, new PodInfo(createdDeployment.getMetadata().getName(), serviceIP, connectivity));
            usedPorts.addAll(newPorts);

            return connectivity;
        } catch (ApiException e) {
            log.severe("Error creating pod: " + e.getMessage());
            throw new IOException("Failed to create Pod: " + e.getMessage());
        }
    }

    private List<V1EnvVar> buildEnv(String containerId, String token, String owner, List<ImageParameter> parameters, Map<String, String> arguments) {
        return config.buildContainerEnv(containerId, token, owner, parameters, arguments).entrySet().stream()
                .map(e -> new V1EnvVar().name(e.getKey()).value(e.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public void stopContainer(String containerId) throws IOException {
        try {
            // remove container info, stop container
            var containerInfo = pods.remove(containerId);
            appsApi.deleteNamespacedDeployment(containerId, namespace).execute();
            coreApi.deleteNamespacedService(serviceId(containerId), namespace).execute();
            
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
    public boolean isContainerAlive(String containerId) throws IOException {
        // TODO implement this method for Kubernetes!
        return true;
    }

    @Override
    public String getUrl(String podId) {
        var ip = pods.get(podId).getInternalIp();
        return String.format("http://%s:%s", ip, AgentContainerApi.DEFAULT_PORT);
    }

    @Override
    protected String getContainerBaseUrl() {
        return config.getOwnBaseUrl().replaceAll(":\\d+$", "");
    }

    private void createServicesForPorts(String containerId, AgentContainerImage image, Map<Integer, Integer> portMap) throws ApiException {
        for (Map.Entry<Integer, Integer> entry : portMap.entrySet()) {
            if (entry.getKey().equals(image.getApiPort())) continue; // Skip api port
            int containerPort = entry.getKey();
            int hostPort = entry.getValue();
            String protocol = image.getExtraPorts().get(containerPort).getProtocol();
            V1Service service = createNodePortService(containerId, hostPort, containerPort, protocol);

            // could not expose port as service -> could not create pod
            coreApi.createNamespacedService(namespace, service).execute();
        }
    }

    private V1Service createNodePortService(String containerId, int port, int targetPort, String protocol) {
        String serviceName = serviceId(containerId) + "-" + port;
        return new V1Service()
            .metadata(new V1ObjectMeta().name(serviceName).labels(Map.of("app", containerId)))
            .spec(new V1ServiceSpec()
                .type("NodePort")
                .selector(Map.of("app", containerId))
                .ports(List.of(
                    new V1ServicePort()
                        .port(port)
                        .targetPort(new IntOrString(targetPort))
                        .nodePort(port) 
                        .protocol(protocol)
                ))
            );
    }

    private String serviceId(String containerId) {
        return "svc-" + containerId;
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
            this.coreApi.createNamespacedSecret(namespace, secret).execute();
        } catch (ApiException e) {
            log.severe("Exception when creating secret: " + e.getResponseBody());
        }

        return secretName;
    }

}
