package de.gtarc.opaca.container

import de.gtarc.opaca.api.AgentContainerApi
import de.gtarc.opaca.util.ApiProxy
import de.gtarc.opaca.util.KeycloakUtil
import kotlin.math.log

/**
 * Singleton class providing some helper methods for handling Authentication in the Container. Can also be used as
 * blueprint for transferring this to other implementations. Extracts the relevant values from Environment Variables
 * passed to the container, then uses those to create Tokens for communicating with the Runtime Platform, and for
 * validating the Tokens received from the Runtime Platform.
 */
object AuthHelper {

    /** the ID of the Agent Container itself, received on initialization */
    private val containerId = System.getenv(AgentContainerApi.ENV_CONTAINER_ID)

    /** the URL of the parent Runtime Platform, received on initialization */
    private val runtimePlatformUrl = System.getenv(AgentContainerApi.ENV_PLATFORM_URL)

    /** Keycloak URL and Realm, if Platform is using Auth; else null/empty */
    private var keycloakUrl = System.getenv(AgentContainerApi.ENV_KEYCLOAK_URL)

    /** Client Secret to request a new JWT if Platform is using Auth; Client-ID is Container-ID */
    private var clientSecret = System.getenv(AgentContainerApi.ENV_CLIENT_SECRET)

    /**
     * Get token for communicating with RP, if Keycloak is set, otherwise no token / empty string (will then be ignored)
     * TODO currently, this creates a fresh token with each call; ideally, the token should be cached while its valid.
     */
    fun getToken() = if (keycloakUrl.isNullOrBlank()) null else KeycloakUtil.getTokenForClient(keycloakUrl, containerId, clientSecret)

    /**
     * Get Proxy to parent Runtime Platform, including fresh access token, if necessary.
     */
    fun getParentProxy() = ApiProxy(runtimePlatformUrl, containerId, getToken())

    /**
     * Validate the token received with the HTTP request. Most of this is actually done in KeycloakUtil.
     */
    fun validateToken(token: String?): Boolean {
        if (keycloakUrl.isNullOrBlank()) {
            return true
        } else {
            try {
                KeycloakUtil.validateToken(keycloakUrl, token)
                // validateToken checks signature and expired-time; check anything else here?
                return true
            } catch (e: Exception) {
                return false
            }
        }
    }

}