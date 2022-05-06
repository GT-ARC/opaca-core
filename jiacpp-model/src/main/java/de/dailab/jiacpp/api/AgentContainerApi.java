package de.dailab.jiacpp.api;

import de.dailab.jiacpp.model.AgentContainer;

import java.io.IOException;

/**
 * Agent-Container-specific additions on top of the Common API. Basically, this is just
 * a single route for retrieving information on the container. The Runtime Platform has
 * the same route, but with different return type.
 */
public interface AgentContainerApi extends CommonApi{

    // TODO REST routes are still preliminary

    /**
     * Get information on the container, to be called by the Runtime Platform after start
     *
     * REST Route: GET /info
     *
     * @return  Information on the started container
     */
    AgentContainer getInfo() throws IOException;

    /**
     * After being started, tell this AgentContainer its Container-ID and the URL of the parent
     * RuntimePlatform. Should have no effect (and return False) if already initialized.
     *
     * @param containerId this container's unique container ID, given by the platform (or e.g. Docker)
     * @param platformUrl the URL where to find this container's parent runtime platform
     * @return true if initialization successful and has not been initialized before, otherwise false
     * @throws IOException
     */
    boolean initialize(String containerId, String platformUrl) throws IOException;

}
