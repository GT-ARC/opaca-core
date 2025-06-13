package de.gtarc.opaca.platform.containerclient;

import com.google.common.base.Strings;
import de.gtarc.opaca.model.AgentContainer;
import de.gtarc.opaca.model.AgentContainerImage;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Common superclass for different Container Clients implementing some common functions
 */
abstract public class AbstractContainerClient implements ContainerClient {

    /** Set of already used ports on target host */
    protected Set<Integer> usedPorts;


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
        try (var s1 = new ServerSocket(port); var s2 = new DatagramSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    protected abstract String getContainerBaseUrl();

}
