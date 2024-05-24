package de.gtarc.opaca.container

import de.gtarc.opaca.model.ErrorResponse
import de.gtarc.opaca.api.AgentContainerApi
import de.gtarc.opaca.model.Message
import de.gtarc.opaca.util.RestHelper
import io.javalin.Javalin

/**
 * New version of the server providing the REST routes for the OPACA Agent Container API using
 * Javalin. Internally, this uses Jetty and is still very lightweight, but has a much nicer API
 * to work with and define the different routes. The functionality is still limited (intentionally)
 * since it should only be used by the OPACA Runtime Platform, which provides features like the
 * web UI for the user.
 * 
 * Requests arriving here are executed in a thread of the underlying Jetty HTTP handler, which then
 * calls functions of the API Implementation and the Container Agent (still in that thread!). Any
 * callbacks, e.g. for invoke-ask, are then handled by the Container Agent's thread. This allows
 * for concurrent execution, but in some cases still causes problems if many requests arrive at
 * the same time (and is not really nice in any case).
 */
class RestServerJavalin(val impl: AgentContainerApi, val port: Int, val token: String?) {

    private val errorStatusCodes = mutableMapOf<Class<out Exception>, Int>()

    private val server = Javalin.create()
            .before {
                val tokenFromRequest = it.header("Authorization")?.removePrefix("Bearer ")
                if (! token.isNullOrEmpty() && tokenFromRequest != token) {
                    throw NotAuthenticatedException("Unauthorized: Token does not match")
                }
            }
            .get("/info") {
                it.json(impl.containerInfo)
            }
            .get("/agents") {
                it.json(impl.agents)
            }
            .get("/agents/{agentId}") {
                it.json(impl.getAgent(it.pathParam("agentId")))
            }
            .get("/stream/{stream}") {
                it.contentType("application/octet-stream")
                it.result(impl.getStream(it.pathParam("stream"), null, "", false))
            }
            .get("/stream/{stream}/{agentId}") { 
                it.contentType("application/octet-stream")
                it.result(impl.getStream(it.pathParam("stream"), it.pathParam("agentId"), "", false))
            }
            .post("/send/{agentId}") {
                val id = it.pathParam("agentId")
                val message = RestHelper.readObject(it.body(), Message::class.java)
                impl.send(id, message, "", false)
            }
            .post("/broadcast/{channel}") {
                val channel = it.pathParam("channel")
                val message = RestHelper.readObject(it.body(), Message::class.java)
                impl.broadcast(channel, message, "", false)
            }
            .post("/invoke/{action}") {
                val action = it.pathParam("action")
                val timeout = (it.queryParam("timeout") ?: "-1").toInt()
                val parameters = RestHelper.readMap(it.body())
                it.json(impl.invoke(action, parameters, null, timeout, "", false))
            }
            .post("/invoke/{action}/{agentId}") {
                val action = it.pathParam("action")
                val agentId = it.pathParam("agentId")
                val timeout = (it.queryParam("timeout") ?: "-1").toInt()
                val parameters = RestHelper.readMap(it.body())
                it.json(impl.invoke(action, parameters, agentId, timeout, "", false))
            }
            .post("/stream/{stream}") {
                val stream = it.pathParam("stream")
                impl.postStream(stream, it.bodyAsBytes(), null, "", false)
            }
            .post("/stream/{stream}/{agentId}") {
                val stream = it.pathParam("stream")
                val agentId = it.pathParam("agentId")
                impl.postStream(stream, it.bodyAsBytes(), agentId, "", false)
            }
            .exception(Exception::class.java) { e, ctx -> 
                val code = when (e) {
                    is OpacaException -> e.statusCode
                    else -> errorStatusCodes[e::class.java] ?: 500
                }
                val err = ErrorResponse(code, e.message, null)
                ctx.status(code)
                ctx.json(err)
            }
    
    init {
        registerErrorCode(NoSuchElementException::class.java, 404)
        registerErrorCode(NotAuthenticatedException::class.java, 403)
    }
                    
    fun start() {
        server.start(port)
    }

    fun stop() {
        server.stop()
    }

    fun registerErrorCode(exceptionClass: Class<out Exception>, code: Int) {
        errorStatusCodes[exceptionClass] = code
    }

    class NotAuthenticatedException(message: String): RuntimeException(message)
    class OpacaException(val statusCode: Int, message: String): Exception(message)
}

