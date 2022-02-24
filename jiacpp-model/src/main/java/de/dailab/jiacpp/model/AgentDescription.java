package de.dailab.jiacpp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Description of a single running agents, including its capabilities.
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class AgentDescription {

    /** ID of the agent, should be globally unique, e.g. a UUID */
    String agentId;

    /** name/type of the agent, e.g. "VehicleAgent" or similar */
    String agentType;

    /** list of actions provided by this agent, if any */
    List<Action> actions;

}
