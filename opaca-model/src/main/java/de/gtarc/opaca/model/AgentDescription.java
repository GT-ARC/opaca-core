package de.gtarc.opaca.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
    @NotNull
    String agentId;

    /** name/type of the agent, e.g. "VehicleAgent" or similar */
    String agentType;

    /** optional human-readable description of the agent */
    String description;

    /** list of actions provided by this agent, if any */
    @NotNull @Valid
    List<Action> actions = List.of();

    /** list of endpoints for sending or receiving streaming data */
    @NotNull @Valid
    List<Stream> streams = List.of();

}
