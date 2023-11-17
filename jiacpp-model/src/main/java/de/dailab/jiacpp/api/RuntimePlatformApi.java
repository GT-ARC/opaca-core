package de.dailab.jiacpp.api;

import de.dailab.jiacpp.model.*;

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
     * @param username The name of the user
     * @param password The password
     * @return JWT access token
     */
    String login(String username, String password) throws IOException;

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
     * @return ID of the started container
     */
    String addContainer(PostAgentContainer container) throws IOException;

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
     * The connection will be bi-directional.
     *
     * REST: POST /connections
     *
     * @param url The base URL of that other Runtime Platform
     * @return Connection successful?
     */
    boolean connectPlatform(String url, String username, String password) throws IOException;

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
     * Notify Platform of changes in a connected Platform, triggering an update by calling the /info route.
     * Can be called by the platform itself, or by some other entity or the user.
     *
     * REST: POST /connections/notify
     *
     * @param platformUrl The URL of the platform to update.
     * @return true/false depending on whether the update was successful (false = platform not reachable, removed)
     */
    boolean notifyUpdatePlatform(String platformUrl) throws IOException;

    /*
     * USER MANAGEMENT
     */

    /**
     * Adds a new user to the connected database.
     *
     * REST: POST /users
     *
     * @param username The unique username of a user
     * @param password The password of a user
     * @param roles A list of assigned roles to the user // TODO probably changes in the future to a list
     * @return true if adding a new user was successful, false otherwise
     */
    boolean addUser(String username, String password, String roles) throws IOException;

    /**
     * Deletes a user from the connected database.
     *
     * REST: DELETE /users/{username}
     *
     * @param username The unique username of a user
     * @return True if the deletion was successful, false otherwise
     */
    boolean deleteUser(String username) throws IOException;

    /**
     * Returns a single user from the connected database by its unique username.
     *
     * REST: GET /users/{username}
     *
     * @param username The unique username of a user
     * @return Information about a single user
     */
    String getUser(String username) throws IOException;

    /**
     * Get list of information about all users registered to the connected database.
     *
     * REST: GET /users
     *
     * @return List of all users in the database
     */
    List<String> getUsers() throws IOException;

    /**
     * Updates an existing user with new information
     *
     * @param username The unique username of a user
     * @param newUsername The new unique username of a user
     * @param password The password of a user
     * @param roles A list of assigned roles to the user // TODO probably changes in the future to a list
     * @return The updated user information
     */
    String updateUser(String username, String newUsername, String password, String roles) throws IOException;

}
