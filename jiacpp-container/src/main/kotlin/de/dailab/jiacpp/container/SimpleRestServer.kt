package de.dailab.jiacpp.container

import de.dailab.jiacpp.api.AgentContainerApi
import de.dailab.jiacpp.model.Message
import de.dailab.jiacpp.util.RestHelper
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletHandler
import org.eclipse.jetty.servlet.ServletHolder
import java.util.stream.Collectors


/**
 * Minimal Jetty server for providing the REST interface. There's probably a better way to do this.
 * The goal is just a minimal REST server for providing the AgentContainer API to be called by the
 * Runtime Platform; things like a web interface, model checking, security, etc. should be provided
 * by the Runtime Platform and are thus intentionally not considered here.
 *
 * Previously, this was a part of the ContainerAgent itself. Moved to a separate class to unclutter
 * the ContainerAgent and "make room" for some basic exception handling, translating no-such-element
 * or actual internal errors (e.g. when executing an action) to appropriate HTTP status codes.
 */
class JiacppServer(val impl: AgentContainerApi, val port: Int) {

    private val server = Server(port)

    /**
     * servlet handling the different REST routes, delegating to `impl` for actual logic
     */
    private val servlet = object: HttpServlet() {

        override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
            try {
                val path = request.pathInfo
                val res = handleGet(path)
                writeResponse(response, 200, res)
            } catch (e: Exception) {
                handleError(response, e)
            }
        }

        override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
            try {
                val path = request.pathInfo  // NOTE: queryParams (?...) go to request.queryString
                val body: String = request.reader.lines().collect(Collectors.joining())
                val res = handlePost(path, body)
                writeResponse(response, 200, res)
            } catch (e: Exception) {
                handleError(response, e)
            }
        }

        private fun writeResponse(response: HttpServletResponse, code: Int, result: Any?) {
            response.contentType = "application/json"
            response.status = code
            response.writer.write(RestHelper.writeJson(result))
        }

        private fun handleError(response: HttpServletResponse, e: Exception) {
            println("HANDLE ERROR $e")
            val code = when (e) {
                is NoSuchMethodException -> 405
                is NoSuchElementException -> 404
                else -> 500
            }
            val err = mapOf(Pair("details", e.toString()))
            writeResponse(response, code, err)
        }

        private fun handleGet(path: String): Any? {

            val info = Regex("^/info$").find(path)
            if (info != null) {
                return impl.containerInfo
            }

            val agents = Regex("^/agents$").find(path)
            if (agents != null) {
                return  impl.agents
            }

            val agentWithId = Regex("^/agents/([^/]+)$").find(path)
            if (agentWithId != null) {
                val id = agentWithId.groupValues[1]
                return impl.getAgent(id)
            }

            throw NoSuchElementException("Unknown path: $path")
        }

        private fun handlePost(path: String, body: String): Any? {

            val send = Regex("^/send/([^/]+)$").find(path)
            if (send != null) {
                val id = send.groupValues[1]
                val message = RestHelper.readObject(body, Message::class.java)
                return impl.send(id, message, "", false)
            }

            val broadcast = Regex("^/broadcast/([^/]+)$").find(path)
            if (broadcast != null) {
                val channel = broadcast.groupValues[1]
                val message = RestHelper.readObject(body, Message::class.java)
                return impl.broadcast(channel, message, "", false)
            }

            val invokeAct = Regex("^/invoke/([^/]+)$").find(path)
            if (invokeAct != null) {
                val action = invokeAct.groupValues[1]
                val parameters = RestHelper.readMap(body)
                return impl.invoke(action, parameters, "", false)
            }

            val invokeActOf = Regex("^/invoke/([^/]+)/([^/]+)$").find(path)
            if (invokeActOf != null) {
                val action = invokeActOf.groupValues[1]
                val agentId = invokeActOf.groupValues[2]
                val parameters = RestHelper.readMap(body)
                return impl.invoke(action, parameters, agentId, "", false)
            }

            throw NoSuchElementException("Unknown path: $path")
        }

    }

    fun start() {
        val handler = ServletHandler()
        handler.addServletWithMapping(ServletHolder(servlet), "/*")
        server.handler = handler
        server.start()
    }

    fun stop() {
        server.stop()
    }

}
