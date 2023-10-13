package de.dailab.jiacpp.container

import com.fasterxml.jackson.databind.JsonNode
import de.dailab.jiacpp.api.AgentContainerApi
import de.dailab.jiacpp.model.*
import de.dailab.jiacpp.util.ApiProxy
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
import java.lang.RuntimeException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Collectors
import java.io.OutputStreamWriter
import java.io.InputStream
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import org.apache.commons.io.IOUtils


const val CONTAINER_AGENT = "container-agent"

/**
 * Agent providing the REST interface of the Agent Container using a simple Jetty server.
 * The API is not quite as fancy one provided using Spring Boot or similar, but might be
 * sufficient, since all "outside" calls would go through the Runtime Container.
 * Still, might be improved a bit...
 */
class ContainerAgent(val image: AgentContainerImage): Agent(overrideName=CONTAINER_AGENT) {

    private val broker by resolve<BrokerAgentRef>()

    private val server by lazy { JiacppServer(impl, AgentContainerApi.DEFAULT_PORT, token) }

    // information on current state of agent container

    /** when the Agent Container was initialized */
    private val startedAt = ZonedDateTime.now(ZoneId.of("Z"))

    /** the ID of the Agent Container itself, received on initialization */
    private val containerId = System.getenv(AgentContainerApi.ENV_CONTAINER_ID)

    /** the URL of the parent Runtime Platform, received on initialization */
    private val runtimePlatformUrl = System.getenv(AgentContainerApi.ENV_PLATFORM_URL)

    /** the token for accessing the parent Runtime Platform, received on initialization */
    private val token = System.getenv(AgentContainerApi.ENV_TOKEN)

    private val parentProxy: ApiProxy by lazy { ApiProxy(runtimePlatformUrl, token) }

    /** other agents registered at the container agent (not all agents are exposed automatically) */
    private val registeredAgents = mutableMapOf<String, AgentDescription>()


    /**
     * Start the Web Server.
     */
    override fun preStart() {
        log.info("Starting Container Agent...")
        super.preStart()
        server.start()
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
            } else {
                throw NoSuchElementException("Agent $agentId not found")
            }
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
                val result = AtomicReference<Any?>() // holder for action result
                val error = AtomicReference<Any?>() // holder for error, if any
                val ref = system.resolve(agent)
                ref invoke ask<Any>(Invoke(action, parameters)) {
                    log.info("GOT RESULT $it")
                    result.set(it)
                    lock.release()
                }.error {
                    log.error("ERROR $it")
                    error.set(it)
                    lock.release()
                }
                // TODO handle timeout?

                log.debug("waiting for action result...")
                lock.acquireUninterruptibly()
                if (error.get() == null) {
                    return RestHelper.mapper.valueToTree(result.get())
                } else {
                    when (val e = error.get()) {
                        is Throwable -> throw RuntimeException(e)
                        else -> throw RuntimeException(e.toString())
                    }
                }
            } else {
                throw NoSuchElementException("Action $action of Agent $agentId not found")
            }
        }


        override fun getStream(action: String, containerId: String, forward: Boolean): ResponseEntity<StreamingResponseBody>? {
            log.info("GET STREAM ACTION: $action")
            return getStream(action, null, containerId, forward)
        }
        
        override fun getStream(action: String, agentId: String?, containerId: String, forward: Boolean): ResponseEntity<StreamingResponseBody>? {
            log.info("GET STREAM ACTION OF AGENT: $agentId $action")

            val agent = findRegisteredAgent(agentId, action)
            if (agent != null) {
                val lock = Semaphore(0)
                val result = AtomicReference<InputStream?>()
                val error = AtomicReference<Any?>()
                val ref = system.resolve(agent)

                ref invoke ask<InputStream>(Stream(action)) { inputStream ->
                    log.info("GOT RESULT $inputStream")
                    result.set(inputStream)
                    lock.release()
                }.error { errorResult ->
                    log.error("ERROR $errorResult")
                    error.set(errorResult)
                    lock.release()
                }

                log.debug("waiting for action result...")
                lock.acquireUninterruptibly()

                if (error.get() == null) {
                    val body = StreamingResponseBody { outputStream ->
                        val inputStream = result.get()
                        if (inputStream != null) {
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                            }
                            outputStream.flush()
                            inputStream.close()
                        }
                        outputStream.close()
                    }
                    return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(body)
                } else {
                    when (val e = error.get()) {
                        is Throwable -> throw RuntimeException(e)
                        else -> throw RuntimeException(e.toString())
                    }
                }
            } else {
                throw NoSuchElementException("Action $action of Agent $agentId not found")
            }
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
            if (it.notify) {
                notifyPlatform()
            }
            Registered(runtimePlatformUrl, token)
        }

        // in case agents want to de-register themselves before the container as a whole terminates
        on<DeRegister> {
            log.info("De-Registering ${it.agentId}")
            registeredAgents.remove(it.agentId)
            if (it.notify) {
                notifyPlatform()
            }
        }

    }

    private fun notifyPlatform() {
        parentProxy.notifyUpdateContainer(containerId)
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
