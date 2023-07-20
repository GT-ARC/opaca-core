package de.dailab.jiacpp.platform;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.dailab.jiacpp.model.AgentContainer;
import de.dailab.jiacpp.model.RuntimePlatform;
import de.dailab.jiacpp.platform.containerclient.DockerClient.DockerContainerInfo;

@Component
public class Persistent {
    /* PlatformImpl variables */
    public Map<String, String> tokens = new HashMap<>();
    public Map<String, AgentContainer> runningContainers = new HashMap<>();
    public Map<String, RuntimePlatform> connectedPlatforms = new HashMap<>();
    public Set<String> pendingConnections = new HashSet<>();
    /* DockerClient variables */
    public Map<String, DockerContainerInfo> dockerContainers = new HashMap<>();
    public Set<Integer> usedPorts = new HashSet<>();
    /* TokensUserDetailsService variables */
    public Map<String, String> userCredentials = new HashMap<>();


}
