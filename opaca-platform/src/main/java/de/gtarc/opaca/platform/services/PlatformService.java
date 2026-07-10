package de.gtarc.opaca.platform.services;

import de.gtarc.opaca.api.PlatformApi;
import de.gtarc.opaca.model.*;
import de.gtarc.opaca.platform.PlatformConfig;
import de.gtarc.opaca.platform.auth.AuthUtils;
import de.gtarc.opaca.platform.session.SessionData;
import de.gtarc.opaca.util.ApiProxy;
import de.gtarc.opaca.util.EventHistory;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
@Component
public class PlatformService implements PlatformApi {

    @Autowired
    private SessionData sessionData;

    @Autowired
    private PlatformConfig config;

    @Autowired
    private AuthUtils authUtils;


    /** when the platform was started */
    private final ZonedDateTime startedAt = ZonedDateTime.now(ZoneId.of("Z"));

    /*
     * API ROUTES
     */

    @Override
    public RuntimePlatform getPlatformInfo() {
        return new RuntimePlatform(
                authUtils.platformId,
                config.getOwnBaseUrl(),
                List.copyOf(sessionData.runningContainers.values()),
                getFullPlatformProvisions(),
                List.copyOf(sessionData.connectedPlatforms.keySet()),
                startedAt
        );
    }

    @Override
    public Map<String, ?> getPlatformConfig() {
        return config.toMap();
    }

    @Override
    public List<Event> getHistory() {
        return EventHistory.getInstance().getEvents();
    }

    @Override
    public String platformLogin(Login loginParams) throws IOException {
        try {
            return authUtils.getTokenForUser(loginParams.getUsername(), loginParams.getPassword());
        } catch (IOException e) {
            throw new BadCredentialsException(e.getMessage());
        }
    }

    @Override
    public List<AgentDescription> getAllAgents() {
        return sessionData.streamAgents(true).collect(Collectors.toList());
    }

    @Override
    public List<AgentContainer> getAllContainers() {
        return sessionData.streamContainers(true).toList();
    }

    /*
     * HELPER METHODS
     */

    public void testSelfConnection() throws Exception {
        var token = authUtils.getPlatformToken();
        var info = new ApiProxy(config.getOwnBaseUrl(), null, token).withTimeout(5000).getPlatformInfo();
        if (! Objects.equals(authUtils.platformId, info.getPlatformId())) {
            throw new IllegalArgumentException("Mismatched Platform ID");
        }
    }

    /**
     * Get the full set of provisions of the Platform, including some config values, provisions
     * from deployed Agent Container images, their agents and actions, etc.
     *
     * @return List of distinct provisions of the Runtime Platform
     */
    protected List<String> getFullPlatformProvisions() {
        List<String> provisions = new ArrayList<>();
        // TODO explicitly set in some env var?

        // from config, e.g. container environment
        Map<String, ?> config = getPlatformConfig();
        provisions.add("config:container-env=" + config.get("containerEnvironment"));
        provisions.add("config:platform-env=" + config.get("platformEnvironment"));
        provisions.add("config:session-policy=" + config.get("sessionPolicy"));
        provisions.add("config:require-auth=" + config.get("requireAuth"));

        // from containers, agents, actions
        for (AgentContainer container : getPlatformInfo().getContainers()) {
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
    }

    /**
     * Check if there are any missing requirements for starting this image and return them.
     * If all requirements are met, it returns an empty list.
     *
     * @param image The Agent Container image to be started
     * @return Unsatisfied requirements, or empty list if all met
     */
    protected Set<String> checkFailedRequirements(AgentContainerImage image) {
        var provisions = new HashSet<>(getFullPlatformProvisions());
        // TODO for now, just check exact string matches
        //  later this could be extended to e.g. check for a minimum CUDA version or similar
        return image.getRequires().stream().filter(x -> ! provisions.contains(x)).collect(Collectors.toSet());
    }


}
