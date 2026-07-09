package de.gtarc.opaca.api;

import de.gtarc.opaca.model.AgentContainer;
import de.gtarc.opaca.model.PostAgentContainer;

import java.io.IOException;
import java.util.List;

/**
 * Part of the {@link RuntimePlatformApi} concerned with Agent Container management.
 */
public interface ContainersApi {

    /**
     * Deploy a container to the runtime Platform. Check requirements, get actual docker image, and
     * deploy to Docker/Kubernetes, then return the ID of the running Agent Container.
     *
     * REST: POST /containers
     *
     * @param container The container to start
     * @param timeout timeout for starting the container, or -1 for default timeout (as per config)
     * @return ID of the started container
     */
    String addContainer(PostAgentContainer container, int timeout) throws IOException;

    /**
     * Deploy a container to the Runtime Platform, replacing an existing container of the same image.
     * This is a convenience-route useful for development, which allows to quickly update a container
     * without having to first search the existing container, remove that container, and then start
     * the new one.
     *
     * This route will check if there is exactly one running container of the same image (or an older
     * version of the image, matching by image name), and in this case tries to stop the running container
     * and then deploy the new container. Will fail in any other case.
     *
     * REST: PUT /containers
     *
     * @param container The container to start, replacing an existing container of the same image
     * @param timeout timeout for starting the container, or -1 for default timeout (as per config)
     * @return ID of the started container
     */
    String updateContainer(PostAgentContainer container, int timeout) throws IOException;

    /**
     * Get descriptions of all currently running Agent Containers.
     *
     * REST: GET /containers
     *
     * @return List of all running containers
     */
    List<AgentContainer> getContainers() throws IOException;

    /**
     * Get description of a single Agent Container
     *
     * REST: GET /containers/{id}
     *
     * @param containerId ID of the container
     * @return Description of the container
     */
    AgentContainer getContainer(String containerId) throws IOException;

    /**
     * Remove an Agent Container from the platform.
     *
     * REST: DELETE /containers/{id}
     *
     * @param containerId ID of the container
     * @return Removal successful?
     */
    boolean removeContainer(String containerId) throws IOException;

    /**
     * Notify Platform of changes in one of its own containers, triggering an update by calling the /info route.
     * Can be called by the container itself, or by some other entity or the user.
     *
     * REST: POST /containers/notify
     *
     * @param containerId The ID of the container to update.
     * @return true/false depending on whether the update was successful (false = container not reachable, removed)
     */
    boolean notifyUpdateContainer(String containerId) throws IOException;

}
