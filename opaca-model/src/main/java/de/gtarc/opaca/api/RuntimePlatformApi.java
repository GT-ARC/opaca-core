package de.gtarc.opaca.api;

import de.gtarc.opaca.model.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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

    
    /**
     * Complementary to {@link CommonApi#getAgents()}: Get list of Agents running in this Runtime Platform
     * or connected platforms, i.e. the entire list of agents and their actions that can be reached by
     * sending a send/invoke/broadcast to this Runtime Platform with query parameter forward=true.
     *
     * REST: GET /agents?includeConnected=true
     *
     * @return List of Agents running on this Runtime Platform, or connected platforms.
     */
    List<AgentDescription> getAllAgents() throws IOException;

    /** Get Configuration of this Runtime Platform, e.g. what container backend is used, what container registries are
     * available, etc. The details of this may vary depending on the implementation and used backend. Make sure not to
     * give away any secret information like passwords!
     *
     * REST Route: GET /config
     *
     * @return Map mapping config key to value; exact keys can vary.
     */
    Map<String, ?> getPlatformConfig() throws IOException;

    /**
     * Get history of "events" that occurred in this runtime platform
     *
     * REST: GET /history
     *
     * @return list of recent events, most-recent last
     */
    List<Event> getHistory() throws IOException;

    /*
     * AUTHENTICATION
     */

    /**
     * Retrieve Access Token for given user to be passed as header for secured routes.
     *
     * REST: GET /login
     *
     * @param loginParams Bundles the username and password in the request body
     * @return JWT access token
     */
    String login(Login loginParams) throws IOException;

    /**
     * Retrieve new Access Token for already logged in user.
     *
     * REST: GET /token
     *
     * @return JWT access token
     */
    String renewToken() throws IOException;

    /*
     * AGENT CONTAINER ROUTES
     */

    // see CommonApi.java

    /*
     * CONTAINER MANAGEMENT
     */

    /**
     * Deploy a container to the runtime Platform. Check requirements, get actual docker image, and
     * deploy to Docker/Kubernetes, then return the ID of the running Agent Container.
     *
     * REST: POST /containers
     *
     * @param container The container to start
     * @param timeout timeout for starting the container, or -1 for default timeout (as per config)
     * @return ID of the started container
     */
    String addContainer(PostAgentContainer container, int timeout) throws IOException;

    /**
     * Deploy a container to the Runtime Platform, replacing an existing container of the same image.
     * This is a convenience-route useful for development, which allows to quickly update a container
     * without having to first search the existing container, remove that container, and then start
     * the new one.
     * 
     * This route will check if there is exactly one running container of the same image (or an older
     * version of the image, matching by image name), and in this case tries to stop the running container
     * and then deploy the new container. Will fail in any other case.
     * 
     * REST: PUT /containers
     *
     * @param container The container to start, replacing an existing container of the same image
     * @param timeout timeout for starting the container, or -1 for default timeout (as per config)
     * @return ID of the started container
     */
    String updateContainer(PostAgentContainer container, int timeout) throws IOException;

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
     * Notify Platform of changes in one of its own containers, triggering an update by calling the /info route.
     * Can be called by the container itself, or by some other entity or the user.
     *
     * REST: POST /containers/notify
     *
     * @param containerId The ID of the container to update.
     * @return true/false depending on whether the update was successful (false = container not reachable, removed)
     */
    boolean notifyUpdateContainer(String containerId) throws IOException;

    /*
     * CONNECTIONS MANAGEMENT
     */

    /**
     * Connect this platform to another platform, running on a different host.
     * The connection will be bidirectional.
     *
     * REST: POST /connections
     *
     * @param connect Wrapper for the Platform URL along with token and whether to connect back
     * @return Connection successful?
     */
    boolean connectPlatform(ConnectionRequest connect) throws IOException;

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
     * @param disconnect Wrapper for the Platform URL along with token and whether to disconnect back
     * @return Disconnect successful?
     */
    boolean disconnectPlatform(ConnectionRequest disconnect) throws IOException;

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
