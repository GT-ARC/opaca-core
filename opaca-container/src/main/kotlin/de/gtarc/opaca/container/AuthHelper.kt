package de.gtarc.opaca.container

import de.gtarc.opaca.api.AgentContainerApi
import de.gtarc.opaca.util.ApiProxy
import de.gtarc.opaca.util.KeycloakUtil
import java.time.Instant

/**
 * Singleton class providing some helper methods for handling Authentication in the Container. Can also be used as
 * a blueprint for transferring this to other implementations. Extracts the relevant values from Environment Variables
 * passed to the container, then uses those to create Tokens for communicating with the Runtime Platform and for
 * validating the Tokens received from the Runtime Platform.
 */
object AuthHelper {

    /** the ID of the Agent Container itself, received on initialization */
    private val containerId = System.getenv(AgentContainerApi.ENV_CONTAINER_ID)

    /** the URL of the parent Runtime Platform, received on initialization */
    private val runtimePlatformUrl = System.getenv(AgentContainerApi.ENV_PLATFORM_URL)

    /** ID of the parent runtime platform, used for token validation */
    private var runtimePlatformId: String? = null

    /** last time when the parent platform ID was fetched */
    private var lastPlatformIdFetch: Instant = Instant.EPOCH

    /** Keycloak URL and Realm, if Platform is using Auth; else null/empty */
    private val keycloakUrl = System.getenv(AgentContainerApi.ENV_KEYCLOAK_URL)

    /** Client Secret to request a new JWT if the Platform is using Auth; Client-ID is Container-ID */
    private val clientSecret = System.getenv(AgentContainerApi.ENV_CLIENT_SECRET)

    private val noAuth = keycloakUrl.isNullOrBlank()

    /** Proxy to the parent Runtime Platform, including fresh access token, if necessary. */
    val parentProxy: ApiProxy = ApiProxy(runtimePlatformUrl, containerId, getToken())
        .withRefreshTokenCallback(if (noAuth) null else ::getToken)

    /**
     * Get token for communicating with RP if Keycloak is set, otherwise no token / empty string (will then be ignored)
     */
    fun getToken() = if (noAuth) null else KeycloakUtil.getTokenForClient(keycloakUrl, containerId, clientSecret)

    /**
     * Validate the token received with the HTTP request. Most of this is actually done in KeycloakUtil.
     */
    fun validateToken(token: String?): Boolean {
        if (noAuth) {
            return true
        } else {
            try {
                // validateToken checks signature (and thus Issuer) and expired-time
                val claims = KeycloakUtil.validateToken(keycloakUrl, token)
                // check that the token comes from the container's parent platform
                // this way, we can rely on the platform having done all the RBAC checks
                return checkPlatformIdInToken(claims)
            } catch (_: Exception) {
                return false
            }
        }
    }

    /**
     * Check that the client_id from the token matches the container's parent platform ID.
     * This way, users can't access the container directly. All calls have to go via the
     * parent platform, and we can rely on the platform having done all the RBAC checks.
     *
     * This fetches the platform ID from the parent platform's `/info` route and tries to
     * fetch a new one if it does not match the old one, or it is more than 60 seconds old.
     * We do this, instead of just passing the platform ID as an Env Var on container start,
     * so that this also works with "opaca.session_policy=reconnect", where the container's
     * parent platform ID may change. (The URL has to stay the same, otherwise the container
     * would not work anymore anyhow.)
     */
    @Synchronized
    private fun checkPlatformIdInToken(claims: Map<String, Any>): Boolean {
        val clientId = claims["client_id"] ?: claims["azp"]
        if (clientId == null) return false
        if (clientId != runtimePlatformId &&
                lastPlatformIdFetch.isBefore(Instant.now().minusSeconds(60))) {
            lastPlatformIdFetch = Instant.now()
            runtimePlatformId = parentProxy.platformInfo.platformId
        }
        return clientId == runtimePlatformId
    }

}