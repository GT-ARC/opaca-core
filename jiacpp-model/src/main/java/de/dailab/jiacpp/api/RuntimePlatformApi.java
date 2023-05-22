package de.dailab.jiacpp.api;

import de.dailab.jiacpp.model.AgentContainer;
import de.dailab.jiacpp.model.AgentContainerImage;
import de.dailab.jiacpp.model.RuntimePlatform;
import de.dailab.jiacpp.model.Event;

import java.io.IOException;
import java.util.List;

/**
 * API functions for the Runtime Platform. Of course, the platform should provide all those
 * routes as REST services (see routes in Javadocs), so this interface is more of a to-do list
 * and documentation for implementers.
 */
public interface RuntimePlatformApi extends CommonApi {

    /**
     * Get full information on the Runtime Platform, including all running Agent Containers and
     * Agents, connected other platforms, etc.
     *
     * REST Route: GET /info
     *
     * @return Extensive information on the platform and its containers and agents.
     */
    RuntimePlatform getPlatformInfo() throws IOException;


    String login(String username, String password) throws IOException;

    /**
     * Get history of "events" that occurred in this runtime platform
     *
     * REST: GET /history
     *
     * @return list of recent events, most-recent last
     */
    List<Event> getHistory() throws IOException;

    /**
     * Deploy a container to the runtime Platform. Check requirements, get actual docker image, and
     * deploy to Docker/Kubernetes, then return the ID of the running Agent Container.
     *
     * REST: POST /containers
     *
     * @param container The container to start
     * @return ID of the started container
     */
    String addContainer(AgentContainerImage container) throws IOException;

    /**
     * Get descriptions of all currently running Agent Containers.
     *
     * REST: GET /containers
     *
     * @return List of all running containers
     */
    List<AgentContainer> getContainers() throws IOException;

    /**
     * Get description of a single Agent Container
     *
     * REST: GET /containers/{id}
     *
     * @param containerId ID of the container
     * @return Description of the container
     */
    AgentContainer getContainer(String containerId) throws IOException;

    /**
     * Remove an Agent Container from the platform.
     *
     * REST: DELETE /containers/{id}
     *
     * @param containerId ID of the container
     * @return Removal successful?
     */
    boolean removeContainer(String containerId) throws IOException;

    /**
     * Connect this platform to another platform, running on a different host.
     * The connection will be bi-directional.
     *
     * REST: POST /connections
     *
     * @param url The base URL of that other Runtime Platform
     * @return Connection successful?
     */
    boolean connectPlatform(String url) throws IOException;

    /**
     * Get list uf base-URLs of connected other Runtime Platforms
     *
     * REST: GET /connections
     *
     * @return List of base-URLs of connected Platforms
     */
    List<String> getConnections() throws IOException;

    /**
     * Disconnect a previously connected Platform, in both directions.
     *
     * REST: DELETE /connections
     *
     * @param url The base-URL of the platform to disconnect.
     * @return Disconnect successful?
     */
    boolean disconnectPlatform(String url) throws IOException;

    /**
     * Notify Platform of changes in one of its own containers, triggering an update by calling the /info route.
     * Can be called by the container itself, or by some other entity or the user.
     *
     * REST: POST /containers/notify
     *
     * @param containerId The ID of the container to update.
     * @return true/false depending on whether the update was successful (false = container not reachable, removed)
     */
    boolean notifyUpdateContainer(String containerId) throws IOException;

    /**
     * Notify Platform of changes in a connected Platform, triggering an update by calling the /info route.
     * Can be called by the platform itself, or by some other entity or the user.
     *
     * REST: POST /connections/notify
     *
     * @param platformUrl The URL of the platform to update.
     * @return true/false depending on whether the update was successful (false = platform not reachable, removed)
     */
    boolean notifyUpdatePlatform(String platformUrl) throws IOException;

}
