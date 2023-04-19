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

    @Value("${default_images:}")
    public String defaultImages;


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
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            String host = socket.getLocalAddress().getHostAddress();
            return "http://" + host + ":" + serverPort;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
