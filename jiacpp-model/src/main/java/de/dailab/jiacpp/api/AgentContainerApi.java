package de.dailab.jiacpp.api;

import de.dailab.jiacpp.model.AgentContainer;

import java.io.IOException;

/**
 * Agent-Container-specific additions on top of the Common API. Basically, this is just
 * a single route for retrieving information on the container. The Runtime Platform has
 * the same route, but with different return type.
 */
public interface AgentContainerApi extends CommonApi{

    String ENV_CONTAINER_ID = "CONTAINER_ID";

    String ENV_PLATFORM_URL = "PLATFORM_URL";

    String ENV_TOKEN = "TOKEN";

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
