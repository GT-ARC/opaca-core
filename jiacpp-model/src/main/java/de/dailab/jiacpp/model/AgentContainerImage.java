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

    // TODO what does a container actually "provide", besides its agents and their actions?

    // TODO additional ports to be exposed and services those provide, e.g. for streaming API?
    //  otherwise, agent containers are _only_ reachable through the here defined API...

    // REQUIRED attributes for starting a container

    /** full path of the (Docker) Container, including repository and version */
    String imageName;

    /** list of required features, e.g. available agents, actions, or platform features */
    List<String> requires;

    /** special features provided by this container */
    List<String> provides;

    // OPTIONAL attributes for description of the container (e.g. in a repository, or of the container itself)

    /** short readable name of this Agent Container */
    String name;

    /** Optional longer description of what the container does */
    String description;

    /** provider of the container, e.g. institute or researcher */
    String provider;

}
