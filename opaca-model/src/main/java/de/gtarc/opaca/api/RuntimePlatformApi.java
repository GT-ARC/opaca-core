package de.gtarc.opaca.api;

/**
 * API functions for the Runtime Platform. Of course, the platform should provide all those
 * routes as REST services (see routes in Javadocs), so this interface is more of a to-do list
 * and documentation for implementers.
 *
 * The different functions are split up onto several sub-interfaces, so they can be implemented in
 * different services in the Runtime-Platform. This interface groups them all together.
 */
public interface RuntimePlatformApi extends PlatformApi, AgentsApi, ContainersApi, ConnectionsApi {

}
