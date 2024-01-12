package de.gtarc.opaca.platform.session;

import java.util.*;

import de.gtarc.opaca.platform.user.PrivilegeRepository;
import de.gtarc.opaca.platform.user.RoleRepository;
import de.gtarc.opaca.platform.user.TokenUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import de.gtarc.opaca.model.PostAgentContainer;
import de.gtarc.opaca.platform.containerclient.DockerClient;
import de.gtarc.opaca.platform.containerclient.KubernetesClient;
import org.springframework.stereotype.Component;

import de.gtarc.opaca.model.AgentContainer;
import de.gtarc.opaca.model.RuntimePlatform;
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
    public Map<String, DockerClient.DockerContainerInfo> dockerContainers = new HashMap<>();
    public Set<Integer> usedPorts = new HashSet<>();

    /* KubernetesClient variables */
    public Map<String, KubernetesClient.PodInfo> pods = new HashMap<>();

    /* TokenUser In-Memory Database Repositories */
    @Autowired
    public TokenUserRepository tokenUserRepository;
    @Autowired
    public RoleRepository roleRepository;
    @Autowired
    public PrivilegeRepository privilegeRepository;

    public void reset() {
        this.tokens.clear();
        this.runningContainers.clear();
        this.startContainerRequests.clear();
        this.connectedPlatforms.clear();
        this.dockerContainers.clear();
        this.usedPorts.clear();
        this.tokenUserRepository.deleteAll();
        this.tokenUserRepository.flush();
        this.roleRepository.deleteAll();
        this.roleRepository.flush();
        this.privilegeRepository.deleteAll();
        this.privilegeRepository.flush();
        this.pods.clear();
    }

}
