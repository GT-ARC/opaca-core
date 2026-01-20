package de.gtarc.opaca.container

import de.gtarc.opaca.api.AgentContainerApi
import de.gtarc.opaca.util.ApiProxy
import de.gtarc.opaca.util.KeycloakUtil

object AuthHelper {

    /** the ID of the Agent Container itself, received on initialization */
    private val containerId = System.getenv(AgentContainerApi.ENV_CONTAINER_ID)

    /** the URL of the parent Runtime Platform, received on initialization */
    private val runtimePlatformUrl = System.getenv(AgentContainerApi.ENV_PLATFORM_URL)

    /** Keycloak URL and Realm, if Platform is using Auth; else null/empty */
    private var keycloakUrl = System.getenv(AgentContainerApi.ENV_KEYCLOAK_URL)

    /** Client Secret to request a new JWT if Platform is using Auth; Client-ID is Container-ID */
    private var clientSecret = System.getenv(AgentContainerApi.ENV_CLIENT_SECRET)

    fun getParentProxy(): ApiProxy {
        val token = if (keycloakUrl.isNullOrBlank()) "" else KeycloakUtil.getTokenForClient(keycloakUrl, containerId, clientSecret)
        return ApiProxy(runtimePlatformUrl, containerId, token)
    }

    fun validateToken(token: String?): Boolean {
        if (keycloakUrl.isNullOrBlank()) {
            return true
        } else {
            try {
                KeycloakUtil.validateToken(keycloakUrl, token)
                // TODO check JWT claims, or just check that it can be decoded?
                //  should at least check time-to-live
                return true
            } catch (e: Exception) {
                return false
            }
        }
    }

}