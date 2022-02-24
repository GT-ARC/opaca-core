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

}
