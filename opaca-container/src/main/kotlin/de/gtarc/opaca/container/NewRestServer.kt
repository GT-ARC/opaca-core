package de.gtarc.opaca.container

import de.gtarc.opaca.model.ErrorResponse
import de.gtarc.opaca.api.AgentContainerApi
import de.gtarc.opaca.model.Message
import de.gtarc.opaca.util.RestHelper
import io.javalin.Javalin
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.AbstractConnector
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.servlet.ServletHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.thread.QueuedThreadPool
import java.util.stream.Collectors
import java.util.concurrent.TimeUnit
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * TODO update javadoc
 * 
 * Minimal Jetty server for providing the REST interface. There's probably a better way to do this.
 * The goal is just a minimal REST server for providing the AgentContainer API to be called by the
 * Runtime Platform; things like a web interface, model checking, security, etc. should be provided
 * by the Runtime Platform and are thus intentionally not considered here.
 *
 * Previously, this was a part of the ContainerAgent itself. Moved to a separate class to unclutter
 * the ContainerAgent and "make room" for some basic exception handling, translating no-such-element
 * or actual internal errors (e.g. when executing an action) to appropriate HTTP status codes.
 */
class JavalinOpacaServer(val impl: AgentContainerApi, val port: Int, val token: String?) {

    private val errorStatusCodes = mutableMapOf<Class<out Exception>, Int>()

    // TODO move to init?
    // TODO make reusable methods for routes
    // use async to fix the AOT problem???
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
            // TODO custom error handler

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

