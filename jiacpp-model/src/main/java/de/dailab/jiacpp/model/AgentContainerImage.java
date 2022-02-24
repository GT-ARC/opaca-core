package de.dailab.jiacpp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Description of an Agent Container Image to be started on a Runtime Platform.
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class AgentContainerImage {

    // REQUIRED attributes for starting a container

    String imageName;

    List<String> requires;

    List<String> provides;

    // OPTIONAL attributes for description of the container (e.g. in a repository, or of the container itself)

    String name;

    String description;

    String provider;

}
