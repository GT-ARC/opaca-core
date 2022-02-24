package de.dailab.jiacpp.api;

import de.dailab.jiacpp.model.AgentContainer;
import de.dailab.jiacpp.model.AgentDescription;
import de.dailab.jiacpp.model.Message;

import java.util.List;
import java.util.Map;

/**
 * API for both, Agent Containers and Runtime Platform. In fact, those are primarilly the
 * Agent Container functions, but separated here, since the Agent Container will also have
 * a specific "info" route.
 */
public interface CommonApi {

    // TODO move receiver into message envelope and combine send and broadcast methods?
    // TODO REST routes are still preliminary

    /**
     * Get list of Agents running in this Agent Container.
     *
     * REST: GET /agents
     *
     * @return List of Agents running in the container
     */
    List<AgentDescription> getAgents();

    /**
     * Get description of one specific Agent
     *
     * REST: GET /agents/{id}
     *
     * @param agentId ID of the agent
     * @return Descripiton of that agent
     */
    AgentDescription getAgent(String agentId);

    /**
     * Send message to a single agent in the container.
     *
     * REST: POST /send/{id}
     *
     * @param agentId ID of the agent
     * @param message The message envelope
     */
    void send(String agentId, Message message);

    /**
     * Send message to a group of agents, or channel.
     *
     * REST: POST /broadcase/{channel}
     *
     * @param channel Name of the group or channel
     * @param message The message envelope
     */
    void broadcast(String channel, Message message);

    /**
     * Invoke an action provided by any agent on this container.
     *
     * REST: POST /invoke/{action}
     *
     * @param action Name of the action
     * @param parameters Map of Parameters
     * @return Action result
     */
    Object invoke(String action, Map<String, Object> parameters);

    /**
     * Invoke an action provided by a specific agent on this container.
     *
     * REST: POST /invoke/{action}/{agent}
     *
     * @param action Name of the action
     * @param parameters Map of Parameters
     * @return Action result
     */
    Object invoke(String agentId, String action, Map<String, Object> parameters);

}