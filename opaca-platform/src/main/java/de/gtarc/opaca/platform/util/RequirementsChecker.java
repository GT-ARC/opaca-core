package de.gtarc.opaca.platform.util;

import de.gtarc.opaca.api.RuntimePlatformApi;
import de.gtarc.opaca.model.*;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class for checking Container Requirements against what the platform or other containers on the platform
 * provide, e.g. in terms of available agents, actions, or infrastructure features. Provisions can be
 * explicitly specified in an environment variable when the platform starts, or inferred from parts
 * of the platform's configuration, deployed containers, etc.
 */
@Log4j2
public class RequirementsChecker {

    private final RuntimePlatformApi platform;

    public RequirementsChecker(RuntimePlatformApi platform) {
        this.platform = platform;
    }

    /**
     * Get full set of provisions of the Platform, including some config values, provisions
     * from deployed Agent Container images, their agents and actions, etc.
     *
     * @return List of distinct provisions of the Runtime Platform
     */
    public List<String> getFullPlatformProvisions() {
        try {
            List<String> provisions = new ArrayList<>();
            // TODO explicitly set in some env var?

            // from config, e.g. container environment
            Map<String, ?> config = this.platform.getPlatformConfig();
            provisions.add("config:container-env=" + config.get("containerEnvironment"));
            provisions.add("config:platform-env=" + config.get("platformEnvironment"));
            provisions.add("config:session-policy=" + config.get("sessionPolicy"));
            provisions.add("config:enable-auth=" + config.get("enableAuth"));

            // from containers, agents, actions
            for (AgentContainer container : this.platform.getContainers()) {
                provisions.add("image:" + container.getImage().getImageName());
                provisions.addAll(container.getImage().getProvides());
                for (AgentDescription agent : container.getAgents()) {
                    provisions.add("agent:" + agent.getAgentType());
                    for (Action action : agent.getActions()) {
                        provisions.add("action:" + action.getName());
                    }
                }
            }
            return provisions.stream().distinct().toList();
        } catch (IOException e) {
            log.warn("Failed to collect Platform Provisions: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Check if there are any missing requirements for starting this image and return them.
     * If all requirements are met, returns an empty list.
     *
     * @param image The Agent Container image to be started
     * @return Unsatisfied requirements, or empty list if all met
     */
    public Set<String> checkFailedRequirements(AgentContainerImage image) {
        var provisions = new HashSet<>(getFullPlatformProvisions());
        // TODO for now, just check exact string matches
        //  later this could be extended to e.g. check for a minimum CUDA version or similar
        return image.getRequires().stream().filter(x -> ! provisions.contains(x)).collect(Collectors.toSet());
    }

}
