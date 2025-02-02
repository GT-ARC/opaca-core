package de.gtarc.opaca.platform;

import com.google.common.base.Strings;
import de.gtarc.opaca.api.AgentContainerApi;
import de.gtarc.opaca.model.AgentContainerImage;
import de.gtarc.opaca.model.PostAgentContainer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Settings for the Runtime Platform. This is not a part of the OPACA model since
 * most of these settings are platform specific and might be different for different
 * implementations of the Runtime Platform, e.g. using Docker vs. Kubernetes.
 */
@Log
@Configuration
@ToString(exclude = {"registryPasswords", "platformAdminPwd", "secret"})
public class PlatformConfig {

    // GENERAL SETTINGS

    @Value("${server.port}")
    public int serverPort;

    @Value("${public_url}")
    public String publicUrl;

    @Value("${container_environment}")
    public PostAgentContainer.ContainerEnvironment containerEnvironment;

    @Value("${platform_environment}")
    public PlatformEnvironment platformEnvironment;

    @Value("${session_policy}")
    public SessionPolicy sessionPolicy;

    @Value("${container_timeout_sec}")
    public int containerTimeoutSec;

    @Value("${default_image_directory}")
    public String defaultImageDirectory;

    @Value("${event_history_size}")
    public int eventHistorySize;

    @Value("${always_pull_images}")
    public boolean alwaysPullImages;

    // SECURITY & AUTHENTICATION

    @Value("${security.enableAuth}")
    public Boolean enableAuth;

    @Value("${security.secret}")
    public String secret;

    @Value("${platform_admin_user}")
    public String platformAdminUser;

    @Value("${platform_admin_pwd}")
    public String platformAdminPwd;

    // USER MANAGEMENT MONGO DB

    @Value("${db_embed}")
    public Boolean dbEmbed;

    @Value("${db_uri}")
    public String dbURI;

    @Value("${db_name}")
    public String dbName;

    // IMAGE REGISTRY CREDENTIALS

    @Value("${registry_separator}")
    public String registrySeparator;

    @Value("${registry_names}")
    public String registryNames;

    @Value("${registry_logins}")
    public String registryLogins;

    @Value("${registry_passwords}")
    public String registryPasswords;

    // DOCKER (only for container_environment = "docker"

    @Value("${remote_docker_host}")
    public String remoteDockerHost;

    @Value("${remote_docker_port}")
    public String remoteDockerPort;

    // KUBERNETES (only for container_environment = "kubernetes")

    @Value("${kubernetes_namespace}")
    public String kubernetesNamespace;

    @Value("${kubernetes_config}")
    public String kubernetesConfig;

    // cached value; either publicUrl, if set, or derived from runtime environment and port
    private String ownBaseUrl = null;

    @PostConstruct
    private void initialize() {
        log.info("Started with Config: " + this);
    }

    public enum PlatformEnvironment {
        NATIVE, DOCKER, KUBERNETES
    }

    public enum SessionPolicy {
        SHUTDOWN, RESTART, RECONNECT
    }

    public Map<String, Object> toMap() {
        Map<String, Object> res = new LinkedHashMap<>(); // keep insertion order
        // general stuff
        res.put("publicUrl", publicUrl);
        res.put("serverPort", serverPort);
        res.put("containerEnvironment", containerEnvironment);
        res.put("platformEnvironment", platformEnvironment);
        res.put("sessionPolicy", sessionPolicy);
        res.put("containerTimeoutSec", containerTimeoutSec);
        res.put("defaultImageDirectory", defaultImageDirectory);
        res.put("eventHistorySize", eventHistorySize);
        res.put("alwaysPullImages", alwaysPullImages);
        // auth stuff
        res.put("enableAuth", enableAuth);
        // user management stuff
        res.put("dbEmbed", dbEmbed);
        // image registry stuff
        res.put("registryNames", registryNames);
        // docker & kubernetes stuff
        if (containerEnvironment == PostAgentContainer.ContainerEnvironment.DOCKER) {
            res.put("remoteDockerHost", remoteDockerHost);
            res.put("remoteDockerPort", remoteDockerPort);
        }
        if (containerEnvironment == PostAgentContainer.ContainerEnvironment.KUBERNETES) {
            res.put("kubernetesNamespace", kubernetesNamespace);
            res.put("kubernetesConfig", kubernetesConfig);
        }
        return res;
    }

    /**
     * Get Host IP address. Should return preferred outbound address.
     * Adapted from https://stackoverflow.com/a/38342964/1639625
     */
    public String getOwnBaseUrl() {
        if (publicUrl != null) {
            return publicUrl;
        }
        if (ownBaseUrl != null) {
            return ownBaseUrl;
        }
        String host;
        if (platformEnvironment == PlatformEnvironment.NATIVE) {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
                host = socket.getLocalAddress().getHostAddress();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else if (platformEnvironment == PlatformEnvironment.DOCKER) {
            // TODO for now, just require publicUrl != null; later find some way to get IP of host?
            throw new RuntimeException("For PLATFORM_ENVIRONMENT = DOCKER, please always use explicit PUBLIC_URL!");
        } else if (platformEnvironment == PlatformEnvironment.KUBERNETES) {
            host = "agents-platform-service." + kubernetesNamespace + ".svc.cluster.local";
        } else {
            throw new RuntimeException("Error determining base URL: Unsupported environment");
        }
        ownBaseUrl = "http://" + host + ":" + serverPort;
        return ownBaseUrl;
    }

    /**
     * Get Dict mapping Docker registries to auth credentials from settings.
     * Adapted from EMPAIA Job Execution Service
     */
    public List<ImageRegistryAuth> loadDockerAuth() {
        if (Strings.isNullOrEmpty(registryNames)) {
            return List.of();
        }
        var sep = registrySeparator;
        var registries = registryNames.split(sep);
        var logins = registryLogins.split(sep);
        var passwords = registryPasswords.split(sep);

        if (registries.length != logins.length || registries.length != passwords.length) {
            log.warning("Number of Registry Names does not match Login Usernames and Passwords");
            return List.of();
        } else {
            return IntStream.range(0, registries.length)
                    .mapToObj(i -> new ImageRegistryAuth(registries[i], logins[i], passwords[i]))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Get Environment for AgentContainers, including both the standard parameters defined by the Runtime Platform,
     * and any user-defined image-specific parameters.
     */
    public Map<String, String> buildContainerEnv(String containerId, String token, String owner, List<AgentContainerImage.ImageParameter> parameters, Map<String, String> arguments) {
        Map<String, String> env = new HashMap<>();
        // standard env vars passed from Runtime Platform to Agent Container
        env.put(AgentContainerApi.ENV_CONTAINER_ID, containerId);
        env.put(AgentContainerApi.ENV_TOKEN, token);
        env.put(AgentContainerApi.ENV_OWNER, owner);
        env.put(AgentContainerApi.ENV_PLATFORM_URL, getOwnBaseUrl());
        // additional user-defined parameters
        for (AgentContainerImage.ImageParameter param : parameters) {
            if (arguments.containsKey(param.getName())) {
                env.put(param.getName(), arguments.get(param.getName()));
            } else if (! param.isRequired()) {
                env.put(param.getName(), param.getDefaultValue());
            } else {
                throw new IllegalArgumentException("Missing required parameter: " + param.getName());
            }
        }
        return env;
    }

    @Data @AllArgsConstructor
    public static class ImageRegistryAuth {
        String registry;
        String login;
        String password;
    }
}
