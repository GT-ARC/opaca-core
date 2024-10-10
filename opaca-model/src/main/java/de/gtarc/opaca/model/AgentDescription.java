package de.gtarc.opaca.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.List;

/**
 * Description of a single running agents, including its capabilities.
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class AgentDescription {

    // TODO also list messages this agent understands and would react to

    /** ID of the agent, should be globally unique, e.g. a UUID */
    @NonNull
    String agentId;

    /** name/type of the agent, e.g. "VehicleAgent" or similar */
    String agentType;

    /** list of actions provided by this agent, if any */
    @NonNull
    List<Action> actions = List.of();

    List<String> reactions = List.of();
    
    /** list of endpoints for sending or receiving streaming data */
    @NonNull
    List<Stream> streams = List.of();

}
