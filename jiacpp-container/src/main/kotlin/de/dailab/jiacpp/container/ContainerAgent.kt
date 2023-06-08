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
import java.time.ZoneId
import java.time.ZonedDateTime
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
            log.info("received POST $request")
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


    // information on current state of agent container

    /** when the Agent Container was initialized */
    private var startedAt: ZonedDateTime? = null

    /** the ID of the Agent Container itself, received on initialization */
    private var containerId: String? = null

    /** the URL of the parent Runtime Platform, received on initialization */
    private var runtimePlatformUrl: String? = null

    /** the token for accessing the parent Runtime Platform, received on initialization */
    private var token: String? = null
    
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
        token = System.getenv(AgentContainerApi.ENV_TOKEN)
        startedAt = ZonedDateTime.now(ZoneId.of("Z"))
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

        override fun getContainerInfo(): AgentContainer {
            log.info("GET INFO")
            return AgentContainer(containerId, image, agents, startedAt, null)
        }

        override fun getAgents(): List<AgentDescription> {
            log.info("GET AGENTS")
            return registeredAgents.values.toList()
        }

        override fun getAgent(agentId: String?): AgentDescription? {
            log.info("GET AGENT: $agentId")
            return registeredAgents[agentId]
        }

        override fun send(agentId: String, message: Message, containerId: String, forward: Boolean) {
            log.info("SEND: $agentId $message")
            val agent = findRegisteredAgent(agentId, action=null)
            if (agent != null) {
                val ref = system.resolve(agent)
                ref tell message
            }
            // TODO raise exception if not found?
        }

        override fun broadcast(channel: String, message: Message, containerId: String, forward: Boolean) {
            log.info("BROADCAST: $channel $message")
            broker.publish(channel, message)
        }

        override fun invoke(action: String, parameters: Map<String, JsonNode>, containerId: String, forward: Boolean): JsonNode? {
            log.info("INVOKE ACTION: $action $parameters")
            return invoke(action, parameters, null, containerId, forward)
        }

        override fun invoke(action: String, parameters: Map<String, JsonNode>, agentId: String?, containerId: String, forward: Boolean): JsonNode? {
            log.info("INVOKE ACTION OF AGENT: $agentId $action $parameters")

            val agent = findRegisteredAgent(agentId, action)
            if (agent != null) {
                val lock = Semaphore(0) // needs to be released once before it can be acquired
                val result = AtomicReference<Any>() // holder for action result
                val ref = system.resolve(agent)
                ref invoke ask<Any>(Invoke(action, parameters)) {
                    log.info("GOT RESULT $it")
                    result.set(it)
                    lock.release()
                }.error {
                    log.error("ERROR $it")
                    lock.release()
                }
                // TODO handle timeout?

                log.debug("waiting for action result...")
                lock.acquireUninterruptibly()

                // TODO handle error case here... raise exception or return null?
                return RestHelper.mapper.valueToTree(result.get())
            }
            // TODO raise exception if not found?
            return null
        }
    }


    /**
     * React to other agents, e.g. for forwarding their requests to the Runtime Platform
     */
    override fun behaviour() = act {

        // agents may register with the container agent, publishing their ID and actions
        respond<Register, Registered> {
            // TODO should Register message contain the agent's internal name, or is that always equal to the agentId?
            log.info("Registering ${it.description}")
            registeredAgents[it.description.agentId] = it.description
            notifyPlatform()
            Registered(runtimePlatformUrl, token)
        }

        // in case agents want to de-register themselves before the container as a whole terminates
        on<DeRegister> {
            log.info("De-Registering ${it.agentId}")
            registeredAgents.remove(it.agentId)
            notifyPlatform()
        }

    }

    private fun notifyPlatform() {
        // TODO notify parent platform (or connected platform, if that's implemented) of changes in this container
        //  keep track of last time /info route was invoked to reduce unnecessary calls? won't work for multiple
        //  connected platforms. Or just send the notify with a short delay (1 second?)
    }

    private fun findRegisteredAgent(agentId: String?, action: String?): String? {
        return registeredAgents.values
            .filter { agt -> agentId == null || agt.agentId == agentId }
            .filter { agt -> action == null || agt.actions.any { act -> act.name == action } }
            // TODO also check action parameters?
            .map { it.agentId }
            .firstOrNull()
    }

}
