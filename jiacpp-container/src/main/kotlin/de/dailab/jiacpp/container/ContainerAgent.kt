package de.dailab.jiacpp.container

import de.dailab.jiacpp.api.AgentContainerApi
import de.dailab.jiacpp.model.AgentContainer
import de.dailab.jiacpp.model.AgentContainerImage
import de.dailab.jiacpp.model.AgentDescription
import de.dailab.jiacpp.model.Message
import de.dailab.jiacpp.util.RestHelper
import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.behaviour.act
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletHandler
import org.eclipse.jetty.servlet.ServletHolder
import java.util.stream.Collectors

/**
 * Agent providing the REST interface of the Agent Container using a simple Jetty server.
 * The API is not quite as fancy one provided using Spring Boot or similar, but might be
 * sufficient, since all "outside" calls would go through the Runtime Container.
 * Still, might be improved a bit...
 */
class ContainerAgent(val image: AgentContainerImage): Agent(overrideName="container-agent") {

    private val server = Server(8082)

    private val servlet = object : HttpServlet() {

        // I'm sure there's a much better way to do this...

        override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
            log.info("received GET $request")
            val path = request.pathInfo

            val info = Regex("^/info$").find(path)
            if (info != null) {
                val res = impl.info
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
            log.info("received POST $request")
            val path = request.pathInfo
            val body: String = request.reader.lines().collect(Collectors.joining())

            val send = Regex("^/send/([^/]+)$").find(path)
            if (send != null) {
                val id = send.groupValues[1]
                val message = RestHelper.readJson(body, Message::class.java)
                val res = impl.send(id, message)
                response.writer.write(RestHelper.writeJson(res))
            }

            val broadcast = Regex("^/broadcast/([^/]+)$").find(path)
            if (broadcast != null) {
                val channel = broadcast.groupValues[1]
                val message = RestHelper.readJson(body, Message::class.java)
                val res = impl.broadcast(channel, message)
                response.writer.write(RestHelper.writeJson(res))
            }

            val invokeAct = Regex("^/invoke/([^/]+)$").find(path)
            if (invokeAct != null) {
                val action = invokeAct.groupValues[1]
                // TODO this probably won't work properly without the actual types
                val parameters = null // RestHelper.readJson(body, Map::class.java)
                val res = impl.invoke(action, parameters)
                response.writer.write(RestHelper.writeJson(res))
            }

            val invokeActOf = Regex("^/invoke/([^/]+)/([^/]+)$").find(path)
            if (invokeActOf != null) {
                val action = invokeActOf.groupValues[1]
                val agentId = invokeActOf.groupValues[2]
                // TODO this probably won't work properly without the actual types
                val parameters = null // RestHelper.readJson(body, Map::class.java)
                val res = impl.invoke(agentId, action, parameters)
                response.writer.write(RestHelper.writeJson(res))
            }
        }
    }

    private val impl = object : AgentContainerApi {

        override fun getInfo(): AgentContainer {
            println("GET INFO")
            return AgentContainer()
        }

        override fun getAgents(): List<AgentDescription> {
            println("GET AGENTS")
            return listOf<AgentDescription>()
        }

        override fun getAgent(agentId: String?): AgentDescription {
            println("GET AGENT")
            println(agentId)
            return AgentDescription()
        }

        override fun send(agentId: String?, message: Message?) {
            println("SEND")
            println(agentId)
            println(message)
        }

        override fun broadcast(channel: String?, message: Message?) {
            println("BROADCAST")
            println(channel)
            println(message)
        }

        override fun invoke(action: String?, parameters: Map<String, Any>?): Any {
            println("INVOKE ACTION")
            println(action)
            println(parameters)
            return "nothing"
        }

        override fun invoke(agentId: String?, action: String?, parameters: Map<String, Any>?): Any {
            println("INVOKE ACTION OF AGENT")
            println(agentId)
            println(action)
            println(parameters)
            return "nothing"
        }
    }

    // start Web Server
    override fun preStart() {
        super.preStart()
        val handler = ServletHandler()
        handler.addServletWithMapping(ServletHolder(servlet), "/*")
        server.handler = handler
        server.start()
    }

    // stop Web Server
    override fun postStop() {
        server.stop()
        super.postStop()
    }

    override fun behaviour() = act {

        // Container Agent does not have any behaviour of its own?
        // should probably handle messages to be sent to other containers/platforms

    }

}
