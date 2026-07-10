package de.gtarc.opaca.platform;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import de.gtarc.opaca.api.AgentContainerApi;
import de.gtarc.opaca.api.RuntimePlatformApi;
import de.gtarc.opaca.platform.auth.AuthUtils;
import de.gtarc.opaca.platform.containerclient.ContainerClient;
import de.gtarc.opaca.platform.containerclient.DockerClient;
import de.gtarc.opaca.platform.containerclient.KubernetesClient;
import de.gtarc.opaca.platform.session.SessionData;
import de.gtarc.opaca.model.*;
import de.gtarc.opaca.model.AgentContainer.Connectivity;
import de.gtarc.opaca.platform.util.ArgumentValidator;
import de.gtarc.opaca.platform.util.RequirementsChecker;
import de.gtarc.opaca.util.ApiProxy;
import de.gtarc.opaca.util.WebSocketConnector;
import lombok.Getter;
import de.gtarc.opaca.util.EventHistory;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.WebSocket;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.web.server.ResponseStatusException;


/**
 * This class provides the actual implementation of the API routes. Might also be split up
 * further, e.g. for agent-forwarding, container-management, and linking to other platforms.
 *
 * Note that this class is closely related to the {@link RuntimePlatformApi} interface, without
 * actually implementing it. This is due to the access-token being passed as an explicit parameter
 * from the Rest-Controller for some routes where the current user is relevant, whereas for e.g.
 * the {@link ApiProxy} the access-token should always be handled "behind the scenes".
 */
@Log4j2
@Component
public class PlatformImpl implements RuntimePlatformApi {


    /** Map of validators for validating action argument types for each container */
    private final Map<String, ArgumentValidator> validators = new HashMap<>();

    /** internal flag; set to true on shutdown to allow stopping containers without necessary credentials */
    private boolean isShuttingDown = false;


    /*
     * HELPER METHODS
     */





    public void setIsShuttingDown() {
        this.isShuttingDown = true;
    }


}
