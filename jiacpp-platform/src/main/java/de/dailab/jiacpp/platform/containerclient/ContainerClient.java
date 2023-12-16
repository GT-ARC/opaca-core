package de.dailab.jiacpp.platform.containerclient;

import de.gtarc.opaca.model.AgentContainer;
import de.gtarc.opaca.model.PostAgentContainer;
import de.dailab.jiacpp.platform.PlatformConfig;
import de.dailab.jiacpp.platform.session.SessionData;

import java.io.IOException;
import java.util.NoSuchElementException;

/**
 * Abstract interface for different clients for starting Agent Containers, e.g. on Docker or Kubernetes.
 * This interface is used only by PlatformImpl and can be adapted if needed. Besides keeping PlatformImpl
 * somewhat clean, the main point of this is so that the same PlatformImpl can be used with different
 * container clients, e.g. via configuration.
 */
public interface ContainerClient {

    /**
     * Initialize the client using properties in the given configuration file. Different clients may
     * require different attributes.
     */
    void initialize(PlatformConfig config, SessionData SessionData);

    /**
     * Test connection to the Backend, e.g. Docker or Kubernetes. This is called right after initialize,
     * but made this a separate method to make it more explicit. This should raise an Exception (with
     * some details, if possible) that will then crash the Service right after the start.
     */
    void testConnectivity();

    /**
     * Start a container with the given container ID (for later reference) and image name. If all goes well,
     * return nothing, otherwise raise an appropriate exception.
     *
     * @return Port Mappings
     */
    AgentContainer.Connectivity startContainer(String containerId, String token, PostAgentContainer container) throws IOException, NoSuchElementException;

    /**
     * Stop the agent container with the given ID.
     */
    void stopContainer(String containerId) throws IOException;

    /**
     * Get the URL where the container can be reached for forwarding requests.
     */
    String getUrl(String containerId);

}
