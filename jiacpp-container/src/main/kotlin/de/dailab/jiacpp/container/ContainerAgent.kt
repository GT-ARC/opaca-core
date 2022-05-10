package de.dailab.jiacpp.container

import com.fasterxml.jackson.databind.JsonNode
import de.dailab.jiacpp.api.AgentContainerApi
import de.dailab.jiacpp.model.*
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
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Collectors


const val CONTAINER_AGENT = "container-agent"

/**
 * Agent providing the REST interface of the Agent Container using a simple Jetty server.
 * The API is not quite as fancy one provided using Spring Boot or similar, but might be
 * sufficient, since all "outside" calls would go through the Runtime Container.
 * Still, might be improved a bit...
 */
class ContainerAgent(val image: AgentContainerImage): Agent(overrideName=CONTAINER_AGENT) {

    private val broker by resolve<BrokerAgentRef>()


    // implementation of API

    /** minimal Jetty server for providing the REST interface */
    private val server = Server(AgentContainerApi.DEFAULT_PORT)

    /** servlet handling the different REST routes, delegating to `impl` for actual logic */
    private val servlet = object : HttpServlet() {

        // I'm sure there's a much better way to do this...
        // TODO this part would actually be the same for any Java-based implementation, incl. JIAC V...
        //  move this as an abstract class to the Model module to be reusable?

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


    // information on current state of agent container

    /** when the Agent Container was initialized */
    private var startedAt: LocalDateTime? = null

    /** the ID of the Agent Container itself, received on initialization */
    private var containerId: String? = null

    /** the URL of the parent Runtime Platform, received on initialization */
    private var runtimePlatformUrl: String? = null

    /** other agents registered at the container agent (not all agents are exposed automatically) */
    private val registeredAgents = mutableMapOf<String, AgentDescription>()


    /**
     * Start the Web Server.
     */
    override fun preStart() {
        log.info("Starting Container Agent...")
        super.preStart()

        // start web server
        log.info("Starting web server...")
        val handler = ServletHandler()
        handler.addServletWithMapping(ServletHolder(servlet), "/*")
        server.handler = handler
        server.start()

        // get environment variables
        log.info("Setting environment...")
        containerId = System.getenv(AgentContainerApi.ENV_CONTAINER_ID)
        runtimePlatformUrl = System.getenv(AgentContainerApi.ENV_PLATFORM_URL)
        startedAt = LocalDateTime.now()
    }

    /**
     * Stop the Web Server
     */
    override fun postStop() {
        log.info("Stopping Container Agent...")
        server.stop()
        super.postStop()
    }


    /**
     * Implementation of the Agent Container API
     */
    private val impl = object : AgentContainerApi {

        override fun getInfo(): AgentContainer {
            log.info("GET INFO")
            return AgentContainer(containerId, image, agents, startedAt)
        }

        override fun getAgents(): List<AgentDescription> {
            log.info("GET AGENTS")
            return registeredAgents.values.toList()
        }

        override fun getAgent(agentId: String?): AgentDescription? {
            log.info("GET AGENT: $agentId")
            return registeredAgents[agentId]
        }

        override fun send(agentId: String, message: Message) {
            log.info("SEND: $agentId $message")
            // TODO ref not found? does this raise an exception? is this okay?
            val ref = system.resolve(agentId)
            ref tell message
        }

        override fun broadcast(channel: String, message: Message) {
            log.info("BROADCAST: $channel $message")
            broker.publish(channel, message)
        }

        override fun invoke(action: String, parameters: Map<String, JsonNode>): JsonNode? {
            log.info("INVOKE ACTION: $action $parameters")

            val agent = registeredAgents.values.find { ag -> ag.actions.any { ac -> ac.name == action } }
            return if (agent != null) {
                invoke(agent.agentId, action, parameters)
            } else {
                null
            }
        }

        override fun invoke(agentId: String, action: String, parameters: Map<String, JsonNode>): JsonNode? {
            log.info("INVOKE ACTION OF AGENT: $agentId $action $parameters")
            // TODO check if agent has action and parameters match description

            val lock = Semaphore(1)
            lock.acquireUninterruptibly()

            val result = AtomicReference<Any>()

            val ref = system.resolve(agentId)
            ref invoke ask<Any>(Invoke(action, parameters)) {
                log.info("GOT RESULT")
                lock.release()

                result.set(it)
            }.error {
                log.error("errör $it")
                lock.release()
            }
            // TODO handle timeout?

            // TODO does this block the agent or the entire system?
            //  nope, seems to work as intended, but test some more...
            //  Container Agent still reacts to REST calls, e.g. SEND, or another INVOKE...
            //  but when calling the action again on the same agent, it only starts when the first finished
            //  (same thread used for all ask-respond invocations of same agent?
            log.info("waiting...")
            lock.acquireUninterruptibly()

            log.info("done")

            return RestHelper.mapper.valueToTree(result.get())
        }
    }


    /**
     * React to other agents, e.g. for forwarding their requests to the Runtime Platform
     */
    override fun behaviour() = act {

        respond<AgentDescription, Boolean> {
            // TODO agents may register with the container agent, publishing their ID and actions
            // TODO make this a dedicated message class, e.g. RegisterAgent (and also Deregister?)
            registeredAgents[it.agentId] = it
            true
        }

        respond<OutboundInvoke, Any?> {
            // TODO invoke action at parent RuntimePlatform
        }

        on<OutboundMessage> {
            // TODO send message to parent RuntimePlatform
        }

        on<OutboundBroadcast> {
            // TODO send broadcast to parent RuntimePlatform
        }

    }

}
