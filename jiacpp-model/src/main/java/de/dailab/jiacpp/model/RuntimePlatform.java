package de.dailab.jiacpp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Description of the Runtime Platform, including deployed Agent Containers.
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class RuntimePlatform {

    /** the external base URL where to reach this platform */
    String baseUrl;

    /** Agent Containers managed by this platform */
    List<AgentContainer> containers = List.of();

    /** List of capabilities this platform provides, e.g. "gpu-support"; format to be specified */
    List<String> provides = List.of();

    /** List of base URLs of other platforms this platform is connected with */
    List<String> connections = List.of();

}
