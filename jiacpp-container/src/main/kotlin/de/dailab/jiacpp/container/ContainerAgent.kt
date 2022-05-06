package de.dailab.jiacpp.container

import com.fasterxml.jackson.databind.JsonNode
import de.dailab.jiacpp.api.AgentContainerApi
import de.dailab.jiacpp.model.AgentContainer
import de.dailab.jiacpp.model.AgentContainerImage
import de.dailab.jiacpp.model.AgentDescription
import de.dailab.jiacpp.model.Message
import de.dailab.jiacpp.util.RestHelper
import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.BrokerAgentRef
import de.dailab.jiacvi.behaviour.act
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletHandler
import org.eclipse.jetty.servlet.ServletHolder
import java.time.LocalDateTime
import java.util.stream.Collectors

/**
 * Agent providing the REST interface of the Agent Container using a simple Jetty server.
 * The API is not quite as fancy one provided using Spring Boot or similar, but might be
 * sufficient, since all "outside" calls would go through the Runtime Container.
 * Still, might be improved a bit...
 */
class ContainerAgent(val image: AgentContainerImage): Agent(overrideName="container-agent") {

    // implementation of API

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
                val message = RestHelper.readObject(body, Message::class.java)
                val res = impl.send(id, message)
                response.writer.write(RestHelper.writeJson(res))
            }

            val broadcast = Regex("^/broadcast/([^/]+)$").find(path)
            if (broadcast != null) {
                val channel = broadcast.groupValues[1]
                val message = RestHelper.readObject(body, Message::class.java)
                val res = impl.broadcast(channel, message)
                response.writer.write(RestHelper.writeJson(res))
            }

            val invokeAct = Regex("^/invoke/([^/]+)$").find(path)
            if (invokeAct != null) {
                val action = invokeAct.groupValues[1]
                val parameters = RestHelper.readMap(body)
                val res = impl.invoke(action, parameters)
                response.writer.write(RestHelper.writeJson(res))
            }

            val invokeActOf = Regex("^/invoke/([^/]+)/([^/]+)$").find(path)
            if (invokeActOf != null) {
                val action = invokeActOf.groupValues[1]
                val agentId = invokeActOf.groupValues[2]
                val parameters = RestHelper.readMap(body)
                val res = impl.invoke(agentId, action, parameters)
                response.writer.write(RestHelper.writeJson(res))
            }
        }
    }

    private val broker by resolve<BrokerAgentRef>()


    // information on current state of agent container

    private var startedAt: LocalDateTime? = null

    private var containerId: String? = null

    private var runtimePlatformUrl: String? = null

    private val registeredAgents = mutableMapOf<String, AgentDescription>()


    private val impl = object : AgentContainerApi {

        override fun getInfo(): AgentContainer {
            println("GET INFO")
            // TODO how does the Agent Container know it's own ID?
            return AgentContainer(null, image, getAgents(), startedAt)
        }

        override fun initialize(contId: String?, platformUrl: String?): Boolean {
            println("INITIALIZE")
            println(contId)
            println(platformUrl)
            if (containerId == null) {
                containerId = contId
                runtimePlatformUrl = platformUrl
                startedAt = LocalDateTime.now()
                return true
            } else {
                return false
            }
        }

        override fun getAgents(): List<AgentDescription> {
            println("GET AGENTS")
            return registeredAgents.values.toList()
        }

        override fun getAgent(agentId: String?): AgentDescription? {
            println("GET AGENT")
            println(agentId)
            return registeredAgents[agentId]
        }

        override fun send(agentId: String, message: Message) {
            println("SEND")
            println(agentId)
            println(message)
            // TODO ref not found? does this raise an exception? is this okay?
            val ref = system.resolve(agentId)
            ref tell message
        }

        override fun broadcast(channel: String, message: Message) {
            println("BROADCAST")
            println(channel)
            println(message)
            broker.publish(channel, message)
        }

        override fun invoke(action: String, parameters: Map<String, JsonNode>): JsonNode? {
            println("INVOKE ACTION")
            println(action)
            println(parameters)

            val agent = registeredAgents.values.find { ag -> ag.actions.any { ac -> ac.name == action } }
            return if (agent != null) {
                invoke(agent.agentId, action, parameters)
            } else {
                null
            }
        }

        override fun invoke(agentId: String, action: String, parameters: Map<String, JsonNode>): JsonNode? {
            println("INVOKE ACTION OF AGENT")
            println(agentId)
            println(action)
            println(parameters)
            // TODO check if agent has action and parameters match description
            //  invoke action with ask protocol
            //  wait until finished, then return
            return null
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

        respond<AgentDescription, Boolean> {
            // TODO agents may register with the container agent, publishing their ID and actions
            // TODO make this a dedicated message class, e.g. RegisterAgent (and also Deregister?)
            false
        }

        respond<OutboundInvoke, Any?> {
            // TODO invoke action at parent RuntimePlatform
        }

        on<OutboundMessage> {
            // TODO send message to parent RuntimePlatform
        }

        on<OutboundBroadcast> {
            // TODO send broadcase to parent RuntimePlatform
        }

    }

    // TODO placeholders for messages to be forwarded by container agent to platform (and thus to
    //  other containers, possibly connected platforms, etc.)

    class OutboundInvoke()

    class OutboundMessage()

    class OutboundBroadcast()

}
