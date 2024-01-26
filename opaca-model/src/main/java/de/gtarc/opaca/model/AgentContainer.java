package de.gtarc.opaca.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Information on a running Agent Container, combining information on image with runtime data.
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class AgentContainer {

    /** ID of the container; does not necessarily have to be the Docker Container ID */
    @NonNull
    String containerId;

    /** the Image this container was started from */
    @NonNull
    AgentContainerImage image;

    /** Map of Arguments given to the AgentContainer for the Parameters of the Image */
    @NonNull
    Map<String, String> arguments = Map.of();

    /** list of agents running on this container; this might change during its life-time */
    @NonNull
    List<AgentDescription> agents = List.of();

    /** User who started the container; Gives this user special privileges on a container */
    String owner;

    /** when the container was started */
    @NonNull
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "Z")
    ZonedDateTime runningSince;

    /** connectivity information; NOTE: this is not set by the AgentContainer itself, but by the RuntimePlatform! */
    Connectivity connectivity;

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class Connectivity {

        /** this container's public URL (e.g. the URL of the Runtime Platform, Docker Host, or Kubernetes Node */
        @NonNull
        String publicUrl;

        /** where the port where the container provides the OPACA API is mapped to */
        int apiPortMapping;

        /** where additional ports exposed by the container are mapped to */
        @NonNull
        Map<Integer, AgentContainerImage.PortDescription> extraPortMappings = Map.of();

    }

}
