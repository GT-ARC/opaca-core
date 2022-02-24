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

    String agentId;

    String agentType;

    List<Action> actions;

}
