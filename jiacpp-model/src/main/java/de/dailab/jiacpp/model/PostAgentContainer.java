package de.dailab.jiacpp.model;

import com.fasterxml.jackson.databind.JsonNode;
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

    /** Map of Parameters given to the AgentContainer */
    Map<String, String> parameters = Map.of();

}
