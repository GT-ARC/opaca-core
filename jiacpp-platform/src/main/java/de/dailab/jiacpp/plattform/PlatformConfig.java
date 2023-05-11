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

    @Value("${server.port}")
    public String serverPort;

    @Value("${public_url}")
    public String publicUrl;

    @Value("${namespace}")
    public String namespace;

    @Value("${container_environment}")
    public String container_environment;

    @Value("${platform_environment}")
    public String platform_environment;

    @Value("${container_timeout_sec}")
    public Integer containerTimeoutSec;

    @Value("${registry_separator}")
    public String registrySeparator;

    @Value("${registry_names}")
    public String registryNames;

    @Value("${registry_logins}")
    public String registryLogins;

    @Value("${registry_passwords}")
    public String registryPasswords;

    @Value("${kubeconfig}")
    public String kubeconfig;


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
            if (platform_environment.equals("native")) {
                try (DatagramSocket socket = new DatagramSocket()) {
                    socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
                    host = socket.getLocalAddress().getHostAddress();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else if (platform_environment.equals("kubernetes")) {
                host = namespace + "-platform-service." + namespace + ".svc.cluster.local";
            } else {
                throw new RuntimeException("Error determining base URL: Unsupported environment");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error determining base URL", e);
        }

        return "http://" + host + ":" + serverPort;
    }

}
