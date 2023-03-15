package de.dailab.jiacpp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    LocalDateTime runningSince;

    /** where the port where the container provides the JIAC++ API is mapped to */
    Integer apiPortMapping;

    /** where additional ports exposed by the container are mapped to */
    Map<String, PortMappingDescription> extraPortMappings;


    @Data @AllArgsConstructor @NoArgsConstructor
    public static class PortMappingDescription {

        /** the original port inside the container this port is mapped to */
        Integer originalPort;

        /** the protocol that is served via this port */
        String protocol;

        /** human readable description of the service */
        String description;

    }
}
