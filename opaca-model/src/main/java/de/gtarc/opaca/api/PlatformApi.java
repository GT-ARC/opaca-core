package de.gtarc.opaca.api;

import de.gtarc.opaca.model.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Part of the {@link RuntimePlatformApi} concerned with "other" tasks not fitting into the other modules.
 * They are collected here (and not in RuntimePlatformApi itself) so that the different functions can be
 * split up over different implementing classes.
 */
public interface PlatformApi {

    /**
     * Get full information on the Runtime Platform, including all running Agent Containers and
     * Agents, connected other platforms, etc.
     *
     * REST Route: GET /info
     *
     * @return Extensive information on the platform and its containers and agents.
     */
    RuntimePlatform getPlatformInfo() throws IOException;

    /** Get Configuration of this Runtime Platform, e.g., what container backend is used, what container registries are
     * available, etc. The details of this may vary depending on the implementation and used backend. Make sure not to
     * give away any secret information like passwords!
     *
     * REST Route: GET /config
     *
     * @return Map mapping config key to value; exact keys can vary.
     */
    Map<String, ?> getPlatformConfig() throws IOException;

    /**
     * Get the history of "events" that occurred in this runtime platform
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
     * Retrieve Access Token for the given user to be passed as a header for secured routes.
     *
     * REST: POST /login
     *
     * @param loginParams Bundles the username and password in the request body
     * @return JWT access token
     */
    String platformLogin(Login loginParams) throws IOException;

    /*
     * CONNECTED PLATFORM FORWARDING
     */

    /**
     * Complementary to {@link AgentsApi#getAgents()}: Get the list of Agents running in this Runtime Platform
     * or connected platforms, i.e., the entire list of agents and their actions that can be reached by
     * sending a send/invoke/broadcast to this Runtime Platform with query parameter forward=true.
     *
     * REST: GET /agents?includeConnected=true
     *
     * @return List of Agents running on this Runtime Platform, or connected platforms.
     */
    List<AgentDescription> getAllAgents() throws IOException;

    /**
     * Complementary to {@link ContainersApi#getContainers()}: Get the list of all Agent Containers running in this
     * Runtime Platform or connected platforms, i.e., the entire list of agent containers whose actions are reachable
     * via this platform.
     *
     * REST: GET /containers?includeConnected=true
     *
     * @return List of agent containers running on this or on connected platforms
     */
    List<AgentContainer> getAllContainers() throws IOException;

}
