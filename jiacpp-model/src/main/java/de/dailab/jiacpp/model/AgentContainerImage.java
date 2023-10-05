package de.dailab.jiacpp.model;

import de.dailab.jiacpp.api.AgentContainerApi;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Description of an Agent Container Image to be started on a Runtime Platform.
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class AgentContainerImage {

    // TODO what does a container actually "provide", besides its agents and their actions? (Issue #42)

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

    /** version number of this image */
    String version;

    /** Optional longer description of what the container does */
    String description;

    /** provider of the container, e.g. institute or researcher */
    String provider;

    // OPTIONAL attributes for API port (if not default) and extra ports (if any)

    /** the port where the container provides the JIAC++ API; by default this is 8082 but another may be used */
    Integer apiPort;

    /** additional ports exposed by the container and the protocols and services those provide */
    Map<Integer, PortDescription> extraPorts;

    Map<String, ObjectDefinition> definitions;

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class PortDescription {

        /** the protocol that is served via this port */
        String protocol;

        /** human-readable description of the service */
        String description;

    }


    // helper methods allowing the port-attributes to be empty without hassle in other modules

    public Integer getApiPort() {
        return apiPort != null ? apiPort : AgentContainerApi.DEFAULT_PORT;
    }

    public Map<Integer, PortDescription> getExtraPorts() {
        return extraPorts != null ? extraPorts : Map.of();
    }
}
