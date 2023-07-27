package de.dailab.jiacpp.session;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.dailab.jiacpp.model.AgentContainer;
import de.dailab.jiacpp.model.RuntimePlatform;
import de.dailab.jiacpp.platform.containerclient.DockerClient.DockerContainerInfo;
import de.dailab.jiacpp.platform.containerclient.KubernetesClient.PodInfo;
import lombok.Data;

@Data @Component
public class SessionData {

    /* PlatformImpl variables */
    public Map<String, String> tokens = new HashMap<>();
    public Map<String, AgentContainer> runningContainers = new HashMap<>();
    public Map<String, RuntimePlatform> connectedPlatforms = new HashMap<>();
    /* DockerClient variables */
    public Map<String, DockerContainerInfo> dockerContainers = new HashMap<>();
    public Set<Integer> usedPorts = new HashSet<>();
    /* KubernetesClient variables */
    public Map<String, PodInfo> pods = new HashMap<>();
    /* TokensUserDetailsService variables */
    public Map<String, String> userCredentials = new HashMap<>();

    public void reset() {
        this.tokens.clear();
        this.runningContainers.clear();
        this.connectedPlatforms.clear();
        this.dockerContainers.clear();
        this.usedPorts.clear();
        this.userCredentials.clear();
        this.pods.clear();
    }

    public Map<String, AgentContainer> copyRunningContainers() {
        return new HashMap<>(this.runningContainers);
    }
}
