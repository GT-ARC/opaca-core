package de.dailab.jiacpp.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Variant of the AgentContainer description with only the information needed for posting
 * a new container. This could also be a superclass of AgentContainer, but might be better
 * as a separate class (as long as there are not too many attributes) as this disallows a
 * "full" AgentContainer from being posted with all the derived attributes.
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class PostAgentContainer {

    /** the Image this container will be started from */
    AgentContainerImage image;

    /** Map of Arguments given to the AgentContainer for the Parameters of the Image */
    Map<String, String> arguments = Map.of();

    /** optional configuration for container client */
    ClientConfig clientConfig;


    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(value = {
            @JsonSubTypes.Type(value=DockerConfig.class, name="docker"),
            @JsonSubTypes.Type(value=KubernetesConfig.class, name="kubernetes"),
    })
    interface ClientConfig {}

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class DockerConfig implements ClientConfig {

        String type = "docker";

        // nothing here yet, but e.g. gpu-support would be nice
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class KubernetesConfig implements ClientConfig {

        String type = "kubernetes";

        String nodeName;

        boolean hostNetwork;
    }

}
