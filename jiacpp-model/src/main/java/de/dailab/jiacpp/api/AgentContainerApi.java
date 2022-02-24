package de.dailab.jiacpp.api;

import de.dailab.jiacpp.model.AgentContainer;
import de.dailab.jiacpp.model.AgentDescription;
import de.dailab.jiacpp.model.Message;

import java.util.Map;

public interface AgentContainerApi {

    AgentContainer getInfo();

    Map<String, AgentDescription> getAgents();

    AgentDescription getAgent(String agentId);

    void send(String agentId, Message message);

    void broadcast(String channel, Message message);

    Object invoke(String action, Map<String, Object> parameters);

    Object invoke(String agentId, String action, Map<String, Object> parameters);

}
