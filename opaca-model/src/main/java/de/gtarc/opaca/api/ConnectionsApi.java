package de.gtarc.opaca.api;

import de.gtarc.opaca.model.ConnectionRequest;

import java.io.IOException;
import java.util.List;

/**
 * Part of the {@link RuntimePlatformApi} concerned with managing connections to other Runtime Platforms.
 */
public interface ConnectionsApi {

    /**
     * Connect this platform to another platform, running on a different host.
     * The connection will be bidirectional.
     *
     * REST: POST /connections
     *
     * @param connect Wrapper for the Platform URL along with an optional token and whether to connect back
     * @return Connection successful?
     */
    boolean connectPlatform(ConnectionRequest connect) throws IOException;

    /**
     * Get list uf base-URLs of connected other Runtime Platforms
     *
     * REST: GET /connections
     *
     * @return List of base-URLs of connected Platforms
     */
    List<String> getConnections() throws IOException;

    /**
     * Disconnect a previously connected Platform, in both directions.
     *
     * REST: DELETE /connections
     *
     * @param disconnect Wrapper for the Platform URL along with an optional token and whether to disconnect back
     * @return Disconnect successful?
     */
    boolean disconnectPlatform(ConnectionRequest disconnect) throws IOException;

    /**
     * Notify the Platform of changes in a connected Platform, triggering an update by calling the /info route.
     * Can be called by the platform itself, or by some other entity or the user.
     *
     * REST: POST /connections/notify
     *
     * @param platformUrl The URL of the platform to update.
     * @return true/false depending on whether the update was successful (false = platform not reachable, removed)
     */
    boolean notifyUpdatePlatform(String platformUrl) throws IOException;
}
