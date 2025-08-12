package de.gtarc.opaca.api;

import de.gtarc.opaca.model.AgentContainer;

import java.io.IOException;

/**
 * Agent-Container-specific additions on top of the Common API. Basically, this is just
 * a single route for retrieving information on the container. The Runtime Platform has
 * the same route, but with different return type.
 */
public interface AgentContainerApi extends CommonApi{

    /** name of env var holding the container ID */
    String ENV_CONTAINER_ID = "CONTAINER_ID";

    /** name of env var holding the parent platform's URL */
    String ENV_PLATFORM_URL = "PLATFORM_URL";

    /** name of env var holding the container's own access token (if auth is enabled, else null/empty) */
    String ENV_TOKEN = "TOKEN";

    /** name of the user who started the container (if auth is enabled, else null/empty) */
    String ENV_OWNER = "OWNER";

    /** which ports on the host the container's ports are mapped to, in the format "containerPort1:hostPort1,..." */
    String ENV_PORT_MAPPING = "PORT_MAPPING";

    int DEFAULT_PORT = 8082;

    /**
     * Get information on the container, to be called by the Runtime Platform after start
     *
     * REST Route: GET /info
     *
     * @return  Information on the started container
     */
    AgentContainer getContainerInfo() throws IOException;

}
