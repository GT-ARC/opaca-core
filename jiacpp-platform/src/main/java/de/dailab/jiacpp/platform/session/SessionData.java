package de.dailab.jiacpp.platform.session;

import java.util.*;

import de.dailab.jiacpp.model.PostAgentContainer;
import de.dailab.jiacpp.platform.auth.TokenUser;
import org.springframework.stereotype.Component;

import de.dailab.jiacpp.model.AgentContainer;
import de.dailab.jiacpp.model.RuntimePlatform;
import de.dailab.jiacpp.platform.containerclient.DockerClient.DockerContainerInfo;
import de.dailab.jiacpp.platform.containerclient.KubernetesClient.PodInfo;
import lombok.Data;

/**
 * Class aggregating all Session data of the Runtime Platform, to be stored to and loaded from
 * file in between sessions. All other classes (e.g. Runtime-Impl etc.) use the data in this class.
 */
@Data @Component
public class SessionData {

    /* PlatformImpl variables */
    public Map<String, String> tokens = new HashMap<>();
    public Map<String, AgentContainer> runningContainers = new HashMap<>();
    public Map<String, PostAgentContainer> startContainerRequests = new HashMap<>();
    public Map<String, RuntimePlatform> connectedPlatforms = new HashMap<>();

    /* DockerClient variables */
    public Map<String, DockerContainerInfo> dockerContainers = new HashMap<>();
    public Set<Integer> usedPorts = new HashSet<>();

    /* KubernetesClient variables */
    public Map<String, PodInfo> pods = new HashMap<>();

    /* TokensUserDetailsService variables */
    public Map<String, TokenUser> tokenUsers = new HashMap<>();

    public void reset() {
        this.tokens.clear();
        this.runningContainers.clear();
        this.startContainerRequests.clear();
        this.connectedPlatforms.clear();
        this.dockerContainers.clear();
        this.usedPorts.clear();
        this.tokenUsers.clear();
        this.pods.clear();
    }

}
