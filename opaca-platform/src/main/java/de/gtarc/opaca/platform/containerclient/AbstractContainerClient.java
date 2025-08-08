package de.gtarc.opaca.platform.containerclient;

import lombok.extern.java.Log;

import java.io.IOException;
import java.net.*;
import java.util.Set;

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

}
