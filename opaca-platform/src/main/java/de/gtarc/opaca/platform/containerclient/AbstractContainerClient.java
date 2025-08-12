package de.gtarc.opaca.platform.containerclient;

import com.google.common.base.Strings;
import de.gtarc.opaca.api.AgentContainerApi;
import de.gtarc.opaca.model.AgentContainerImage;
import de.gtarc.opaca.platform.PlatformConfig;
import de.gtarc.opaca.platform.session.SessionData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.java.Log;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Common superclass for different Container Clients implementing some common functions
 */
@Log
abstract public class AbstractContainerClient implements ContainerClient {

    protected PlatformConfig config;

    /** Set of already used ports on target host */
    protected Set<Integer> usedPorts;

    @Override
    public void initialize(PlatformConfig config, SessionData sessionData) {
        this.config = config;
        this.usedPorts = sessionData.usedPorts;
    }

    /**
     * Starting from the given preferred port, get and reserve the next free port.
     */
    protected int reserveNextFreePort(int port, Set<Integer> newPorts) {
        while (!isPortAvailable(port, newPorts)) ++port;
        newPorts.add(port);
        return port;
    }

    private boolean isPortAvailable(int port, Set<Integer> newPorts) {
        if (usedPorts.contains(port) || newPorts.contains(port)) return false;
        var host = getContainerBaseUrl().replaceAll("^\\w+://", "");
        return ! isTcpPortOpen(host, port) && ! isUdpPortOpen(host, port);
    }

    private static boolean isTcpPortOpen(String host, int port) {
        try (Socket socket = new Socket(host, port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isUdpPortOpen(String host, int port) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(1000);
            byte[] buffer = new byte[1];
            socket.send(new DatagramPacket(buffer, buffer.length, InetAddress.getByName(host), port));
            socket.receive(new DatagramPacket(buffer, buffer.length)); // assuming response by server
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    protected abstract String getContainerBaseUrl();

    /**
     * Get Dict mapping Docker registries to auth credentials from settings.
     * Adapted from EMPAIA Job Execution Service
     */
    protected List<ImageRegistryAuth> getImageRegistryAuth() {
        if (Strings.isNullOrEmpty(config.registryNames)) {
            return List.of();
        }
        var sep = config.registrySeparator;
        var registries = config.registryNames.split(sep);
        var logins = config.registryLogins.split(sep);
        var passwords = config.registryPasswords.split(sep);

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
    protected Map<String, String> buildContainerEnv(
            String containerId, String token, String owner,
            List<AgentContainerImage.ImageParameter> parameters,
            Map<String, String> arguments,
            Map<Integer, Integer> portMap) {
        Map<String, String> env = new HashMap<>();
        // standard env vars passed from Runtime Platform to Agent Container
        env.put(AgentContainerApi.ENV_CONTAINER_ID, containerId);
        env.put(AgentContainerApi.ENV_TOKEN, token);
        env.put(AgentContainerApi.ENV_OWNER, owner);
        env.put(AgentContainerApi.ENV_PLATFORM_URL, config.getOwnBaseUrl());
        // mapping of container ports to host ports
        env.put(AgentContainerApi.ENV_PORT_MAPPING, portMap.entrySet().stream()
                .map(e -> String.format("%d:%d", e.getKey(), e.getValue()))
                .collect(Collectors.joining(",")));
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

    @Data
    @AllArgsConstructor
    public static class ImageRegistryAuth {
        String registry;
        String login;
        String password;
    }
}
