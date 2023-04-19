package de.dailab.jiacpp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Information on a running Agent Container, combining information on image with runtime data.
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class AgentContainer {

    /** ID of the container; does not necessarily have to be the Docker Container ID */
    String containerId;

    /** the Image this container was started from */
    AgentContainerImage image;

    /** list of agents running on this container; this might change during its life-time */
    List<AgentDescription> agents;

    /** when the container was started */
    String runningSince;

}
