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
 * Minimal Jetty server for providing the REST interface
 * I'm sure there's a much better way to do this...
 */
class JiacppServer(val impl: AgentContainerApi, val port: Int) {

    private val server = Server(port)

    /**
     * servlet handling the different REST routes, delegating to `impl` for actual logic
     */
    private val servlet = object: HttpServlet() {

        override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
            val path = request.pathInfo
            response.contentType = "application/json"

            val info = Regex("^/info$").find(path)
            if (info != null) {
                val res = impl.containerInfo
                response.writer.write(RestHelper.writeJson(res))
            }

            val agents = Regex("^/agents$").find(path)
            if (agents != null) {
                val res = impl.agents
                response.writer.write(RestHelper.writeJson(res))
            }

            val agentWithId = Regex("^/agents/([^/]+)$").find(path)
            if (agentWithId != null) {
                val id = agentWithId.groupValues[1]
                val res = impl.getAgent(id)
                response.writer.write(RestHelper.writeJson(res))
            }
        }

        override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
            val path = request.pathInfo  // NOTE: queryParams (?...) go to request.queryString
            val body: String = request.reader.lines().collect(Collectors.joining())
            response.contentType = "application/json"

            val send = Regex("^/send/([^/]+)$").find(path)
            if (send != null) {
                val id = send.groupValues[1]
                val message = RestHelper.readObject(body, Message::class.java)
                val res = impl.send(id, message, "", false)
                response.writer.write(RestHelper.writeJson(res))
            }

            val broadcast = Regex("^/broadcast/([^/]+)$").find(path)
            if (broadcast != null) {
                val channel = broadcast.groupValues[1]
                val message = RestHelper.readObject(body, Message::class.java)
                val res = impl.broadcast(channel, message, "", false)
                response.writer.write(RestHelper.writeJson(res))
            }

            val invokeAct = Regex("^/invoke/([^/]+)$").find(path)
            if (invokeAct != null) {
                val action = invokeAct.groupValues[1]
                val parameters = RestHelper.readMap(body)
                val res = impl.invoke(action, parameters, "", false)
                response.writer.write(RestHelper.writeJson(res))
            }

            val invokeActOf = Regex("^/invoke/([^/]+)/([^/]+)$").find(path)
            if (invokeActOf != null) {
                val action = invokeActOf.groupValues[1]
                val agentId = invokeActOf.groupValues[2]
                val parameters = RestHelper.readMap(body)
                val res = impl.invoke(action, parameters, agentId, "", false)
                response.writer.write(RestHelper.writeJson(res))
            }
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
