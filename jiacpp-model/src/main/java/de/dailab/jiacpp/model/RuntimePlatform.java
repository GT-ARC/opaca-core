package de.dailab.jiacpp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Description of the Runtime Platform, including deployed Agent Containers.
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class RuntimePlatform {

    String baseUrl;

    Map<String, AgentContainer> containers;

    List<String> provides;

    List<String> connections;

}
