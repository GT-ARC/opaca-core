package de.dailab.jiacpp.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
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

    /** Map of Parameters given to the AgentContainer */
    Map<String, JsonNode> parameters;

    /** list of agents running on this container; this might change during its life-time */
    List<AgentDescription> agents;

    /** when the container was started */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "Z")
    ZonedDateTime runningSince;

    /** connectivity information; NOTE: this is not set by the AgentContainer itself, but by the RuntimePlatform! */
    Connectivity connectivity;

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class Connectivity {

        /** this container's public URL (e.g. the URL of the Runtime Platform, Docker Host, or Kubernetes Node */
        String publicUrl;

        /** where the port where the container provides the JIAC++ API is mapped to */
        Integer apiPortMapping;

        /** where additional ports exposed by the container are mapped to */
        Map<Integer, AgentContainerImage.PortDescription> extraPortMappings;

    }

}
