package de.gtarc.opaca.platform.session;

import java.util.*;
import java.util.stream.Stream;

import de.gtarc.opaca.model.AgentDescription;
import de.gtarc.opaca.model.PostAgentContainer;
import de.gtarc.opaca.platform.containerclient.DockerClient;
import de.gtarc.opaca.platform.containerclient.KubernetesClient;
import org.springframework.stereotype.Component;

import de.gtarc.opaca.model.AgentContainer;
import de.gtarc.opaca.model.RuntimePlatform;
import lombok.Data;

/**
 * Class aggregating all Session data of the Runtime Platform, to be stored to and loaded from
 * a file in between sessions. All other classes (e.g., Runtime-Impl etc.) use the data in this class.
 */
@Component
public class SessionData {

    /* PlatformImpl variables */
    public Map<String, AgentContainer> runningContainers = new HashMap<>();
    public Map<String, PostAgentContainer> startContainerRequests = new HashMap<>();
    public Map<String, RuntimePlatform> connectedPlatforms = new HashMap<>();

    /* Docker/Kubernetes containers state, for "reconnect" policy */
    public Set<Integer> usedPorts = new HashSet<>();
    public Map<String, DockerClient.DockerContainerInfo> dockerContainers = new HashMap<>();
    public Map<String, KubernetesClient.PodInfo> kubernetesPods = new HashMap<>();


    public void reset() {
        this.runningContainers.clear();
        this.startContainerRequests.clear();
        this.connectedPlatforms.clear();
        this.usedPorts.clear();
        this.dockerContainers.clear();
        this.kubernetesPods.clear();
    }

    public Stream<AgentDescription> streamAgents(boolean includeConnected) {
        return streamContainers(includeConnected).flatMap(c -> c.getAgents().stream());
    }

    public Stream<AgentContainer> streamContainers(boolean includeConnected) {
        return includeConnected ? Stream.concat(
                runningContainers.values().stream(),
                connectedPlatforms.values().stream().flatMap(rp -> rp.getContainers().stream())
        ) : runningContainers.values().stream();
    }

}
