package de.dailab.jiacpp.platform;

import com.google.common.base.Strings;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Settings for the Runtime Platform. This is not a part of the JIAC++ model since
 * most of these settings are platform specific and might be different for different
 * implementations of the Runtime Platform, e.g. using Docker vs. Kubernetes.
 */
@Log
@Configuration
@ToString(exclude = {"registryPasswords"})
public class PlatformConfig {

    // GENERAL SETTINGS

    @Value("${server.port}")
    public int serverPort;

    @Value("${public_url}")
    public String publicUrl;

    @Value("${container_environment}")
    public ContainerEnvironment containerEnvironment;

    @Value("${platform_environment}")
    public PlatformEnvironment platformEnvironment;

    @Value("${container_timeout_sec}")
    public int containerTimeoutSec;

    @Value("${default_image_directory}")
    public String defaultImageDirectory;

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


    public enum PlatformEnvironment {
        NATIVE, KUBERNETES
    }

    public enum ContainerEnvironment {
        DOCKER, KUBERNETES
    }

    // TODO
    //  auth stuff for platform itself? tbd
    //  GPU support and other "features" of this specific platform


    /**
     * Get Host IP address. Should return preferred outbound address.
     * Adapted from https://stackoverflow.com/a/38342964/1639625
     */
    public String getOwnBaseUrl() {
        if (publicUrl != null) {
            return publicUrl;
        }

        String host;
        if (platformEnvironment == PlatformEnvironment.NATIVE) {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
                host = socket.getLocalAddress().getHostAddress();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else if (platformEnvironment == PlatformEnvironment.KUBERNETES) {
            host = "agents-platform-service." + kubernetesNamespace + ".svc.cluster.local";
        } else {
            throw new RuntimeException("Error determining base URL: Unsupported environment");
        }
        return "http://" + host + ":" + serverPort;
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

    @Data @AllArgsConstructor
    public static class ImageRegistryAuth {
        String registry;
        String login;
        String password;
    }
}
