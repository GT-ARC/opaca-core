package de.dailab.jiacpp.api;

import com.fasterxml.jackson.databind.JsonNode;
import de.dailab.jiacpp.model.AgentDescription;
import de.dailab.jiacpp.model.Message;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * API for both, Agent Containers and Runtime Platform. In fact, those are primarily the
 * Agent Container functions, but separated here, since the Agent Container will also have
 * a specific "info" route.
 */
public interface CommonApi {

    /**
     * Get list of Agents running in this Agent Container.
     *
     * REST: GET /agents
     *
     * @return List of Agents running in the container
     */
    List<AgentDescription> getAgents() throws IOException;

    /**
     * Get description of one specific Agent
     *
     * REST: GET /agents/{id}
     *
     * @param agentId ID of the agent
     * @return Description of that agent
     */
    AgentDescription getAgent(String agentId) throws IOException;

    /**
     * Send message to a single agent in the container.
     *
     * REST: POST /send/{id}?containerId={containerId}&forward={true|false}`
     *
     * @param agentId ID of the agent
     * @param message The message envelope
     * @param containerId ID of the Container to use (optional)
     * @param forward flag whether to forward the message to connected platforms (optional)
     */
    void send(String agentId, Message message, String containerId, boolean forward) throws IOException;

    /**
     * Send message to a group of agents, or channel.
     *
     * REST: POST /broadcast/{channel}?containerId={containerId}&forward={true|false}`
     *
     * @param channel Name of the group or channel
     * @param message The message envelope
     * @param containerId ID of the Container to use (optional)
     * @param forward flag whether to forward the message to connected platforms (optional)
     */
    void broadcast(String channel, Message message, String containerId, boolean forward) throws IOException;

    /**
     * Invoke an action provided by any agent on this container.
     *
     * REST: POST /invoke/{action}?containerId={containerId}&forward={true|false}`
     *
     * @param action Name of the action
     * @param parameters Map of Parameters
     * @param containerId ID of the Container to use (optional)
     * @param forward flag whether to forward the message to connected platforms (optional)
     * @return Action result
     */
    JsonNode invoke(String action, Map<String, JsonNode> parameters, String containerId, boolean forward) throws IOException;

    /**
     * Invoke an action provided by a specific agent on this container.
     *
     * REST: POST /invoke/{action}/{agent}?containerId={containerId}&forward={true|false}`
     *
     * @param action Name of the action
     * @param parameters Map of Parameters
     * @param agentId Name of the agent
     * @param containerId ID of the Container to use (optional)
     * @param forward flag whether to forward the message to connected platforms (optional)
     * @return Action result
     */
    JsonNode invoke(String action, Map<String, JsonNode> parameters, String agentId, String containerId, boolean forward) throws IOException;

    ResponseEntity<StreamingResponseBody> getStream(String action, String containerId, boolean forward) throws IOException;
    ResponseEntity<StreamingResponseBody> getStream(String action, String agentId, String containerId, boolean forward) throws IOException;

}
