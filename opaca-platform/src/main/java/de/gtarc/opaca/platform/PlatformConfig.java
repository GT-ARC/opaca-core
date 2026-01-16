package de.gtarc.opaca.platform;

import de.gtarc.opaca.model.PostAgentContainer;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings for the Runtime Platform. This is not a part of the OPACA model since
 * most of these settings are platform specific and might be different for different
 * implementations of the Runtime Platform, e.g. using Docker vs. Kubernetes.
 */
@Log4j2
@Configuration
@ToString(exclude = {"registryPasswords", "keycloakAdmin", "keycloakAdminPw"})
public class PlatformConfig {

    // GENERAL SETTINGS

    @Value("${server.port}")
    public int serverPort;

    @Value("${opaca.public_url}")
    public String publicUrl;

    @Value("${opaca.container_environment}")
    public PostAgentContainer.ContainerEnvironment containerEnvironment;

    @Value("${opaca.platform_environment}")
    public PlatformEnvironment platformEnvironment;

    @Value("${opaca.session_policy}")
    public SessionPolicy sessionPolicy;

    @Value("${opaca.container_timeout_sec}")
    public int containerTimeoutSec;

    @Value("${opaca.default_image_directory}")
    public String defaultImageDirectory;

    @Value("${opaca.event_history_size}")
    public int eventHistorySize;

    @Value("${opaca.always_pull_images}")
    public boolean alwaysPullImages;

    // SECURITY & AUTHENTICATION

    @Value("${opaca.security.requireAuth}")
    public Boolean requireAuth;

    @Value("${opaca.security.kc_url}")
    public String keycloakUrl;

    @Value("${opaca.security.kc_realm}")
    public String keycloakRealm;

    @Value("${opaca.security.kc_clientid}")
    public String keycloakClientId;

    @Value("${opaca.security.kc_admin}")
    public String keycloakAdmin;

    @Value("${opaca.security.kc_admin_pw}")
    public String keycloakAdminPw;

    // IMAGE REGISTRY CREDENTIALS

    @Value("${opaca.registry.separator}")
    public String registrySeparator;

    @Value("${opaca.registry.names}")
    public String registryNames;

    @Value("${opaca.registry.logins}")
    public String registryLogins;

    @Value("${opaca.registry.passwords}")
    public String registryPasswords;

    // DOCKER (only for container_environment = "docker"

    @Value("${opaca.docker.remote_host}")
    public String remoteDockerHost;

    @Value("${opaca.docker.remote_port}")
    public String remoteDockerPort;

    // KUBERNETES (only for container_environment = "kubernetes")

    @Value("${opaca.kubernetes.namespace}")
    public String kubernetesNamespace;

    @Value("${opaca.kubernetes.config}")
    public String kubernetesConfig;

    // cached value; either publicUrl, if set, or derived from runtime environment and port
    private String ownBaseUrl = null;

    @PostConstruct
    private void initialize() {
        log.info("Started with Config: {}", this);
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
        res.put("requireAuth", requireAuth);
        res.put("keycloakRealm", keycloakRealm);
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

}
