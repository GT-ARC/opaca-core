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

    String containerId;

    AgentContainerImage image;

    List<AgentDescription> agents;

    LocalDateTime runningSince;

}
