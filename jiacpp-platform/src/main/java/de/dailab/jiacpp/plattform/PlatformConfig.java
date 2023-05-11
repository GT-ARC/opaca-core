package de.dailab.jiacpp.plattform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Settings for the Runtime Platform. This is not a part of the JIAC++ model since
 * most of these settings are platform specific and might be different for different
 * implementations of the Runtime Platform, e.g. using Docker vs. Kubernetes.
 */
@Configuration
public class PlatformConfig {

    // GENERAL SETTINGS

    @Value("${server.port}")
    public String serverPort;

    @Value("${public_url}")
    public String publicUrl;

    @Value("${container_environment}")
    public String containerEnvironment;

    @Value("${platform_environment}")
    public String platformEnvironment;

    @Value("${container_timeout_sec}")
    public Integer containerTimeoutSec;

    // IMAGE REGISTRY CREDENTIALS

    @Value("${registry_separator}")
    public String registrySeparator;

    @Value("${registry_names}")
    public String registryNames;

    @Value("${registry_logins}")
    public String registryLogins;

    @Value("${registry_passwords}")
    public String registryPasswords;

    // KUBERNETES (only for container_environment = "kubernetes")

    @Value("${kubernetes_namespace}")
    public String kubernetesNamespace;

    @Value("${kubernetes_config}")
    public String kubernetesConfig;


    // TODO
    //  (remote) docker host
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

        String host = null;

        try {
            if (platformEnvironment.equals("native")) {
                try (DatagramSocket socket = new DatagramSocket()) {
                    socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
                    host = socket.getLocalAddress().getHostAddress();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else if (platformEnvironment.equals("kubernetes")) {
                host = "agents-platform-service." + kubernetesNamespace + ".svc.cluster.local";
            } else {
                throw new RuntimeException("Error determining base URL: Unsupported environment");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error determining base URL", e);
        }

        return "http://" + host + ":" + serverPort;
    }

}
