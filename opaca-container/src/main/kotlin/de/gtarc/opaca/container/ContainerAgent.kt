package de.gtarc.opaca.container

import com.fasterxml.jackson.databind.JsonNode
import de.gtarc.opaca.api.AgentContainerApi
import de.gtarc.opaca.model.*
import de.gtarc.opaca.util.ApiProxy
import de.gtarc.opaca.util.RestHelper
import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.BrokerAgentRef
import de.dailab.jiacvi.behaviour.act
import java.lang.RuntimeException
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference
import java.io.InputStream


const val CONTAINER_AGENT = "container-agent"

/**
 * Agent providing the REST interface of the Agent Container using a simple Jetty server.
 * The API is not quite as fancy one provided using Spring Boot or similar, but might be
 * sufficient, since all "outside" calls would go through the Runtime Container.
 * Still, might be improved a bit...
 */
class ContainerAgent(val image: AgentContainerImage): Agent(overrideName=CONTAINER_AGENT) {

    private val broker by resolve<BrokerAgentRef>()

    private val server by lazy { OpacaServer(impl, AgentContainerApi.DEFAULT_PORT, token) }

    // information on current state of agent container

    /** when the Agent Container was initialized */
    private val startedAt = ZonedDateTime.now(ZoneId.of("Z"))

    /** the ID of the Agent Container itself, received on initialization */
    private val containerId = System.getenv(AgentContainerApi.ENV_CONTAINER_ID)

    /** the URL of the parent Runtime Platform, received on initialization */
    private val runtimePlatformUrl = System.getenv(AgentContainerApi.ENV_PLATFORM_URL)

    /** the token for accessing the parent Runtime Platform, received on initialization */
    private val token = System.getenv(AgentContainerApi.ENV_TOKEN)

    /** the owner who started the Agent Container */
    private val owner = System.getenv(AgentContainerApi.ENV_OWNER)

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
            return AgentContainer(containerId, image, getParameters(), agents, owner, startedAt, null)
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
            val agent = findRegisteredAgent(agentId, action=null, stream=null)
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

        override fun invoke(action: String, parameters: Map<String, JsonNode>, agentId: String?, timeout: Int, containerId: String, forward: Boolean): JsonNode? {
            log.info("INVOKE ACTION OF AGENT: $agentId $action $parameters")

            val agent = findRegisteredAgent(agentId, action, null)
            if (agent != null) {
                val res: Any = invokeAskWait(agent, Invoke(action, parameters), timeout)
                return RestHelper.mapper.valueToTree(res)
            } else {
                throw NoSuchElementException("Action $action of Agent $agentId not found")
            }
        }

        override fun postStream(stream: String, data: ByteArray, agentId: String?, containerId: String, forward: Boolean) {
            log.info("POST STREAM TO AGENT: $agentId $stream")

            val agent = findRegisteredAgent(agentId, null, stream)
            if (agent != null) {
                val res: Any = invokeAskWait(agent, StreamPost(stream, data), -1)
                // TODO not really needed to wait here?! except for re-throwing an error maybe...
            } else {
                throw NoSuchElementException("Agent $agentId not found for Stream $stream")
            }
        }

        override fun getStream(streamId: String, agentId: String?, containerId: String, forward: Boolean): InputStream? {
            log.info("GET STREAM OF AGENT: $agentId $streamId")

            val agent = findRegisteredAgent(agentId, null, streamId)
            if (agent != null) {
                val inputStream: InputStream = invokeAskWait(agent, StreamGet(streamId), -1)
                return inputStream

            } else {
                throw NoSuchElementException("Stream $streamId of Agent $agentId not found")
            }
        }
    }

    /**
     * Call "ref invoke ask" at given JIAC VI agent, but wait for result and return it.
     */
    private fun <T> invokeAskWait(agentId: String, request: Any, timeout: Int): T {
        val lock = Semaphore(0)
        val result = AtomicReference<T>()
        val error = AtomicReference<Any>()
        val ref = system.resolve(agentId)
        ref invoke ask<T>(request) {
            log.info("GOT RESULT $it")
            result.set(it)
            lock.release()
        }.error {
            log.error("ERROR $it")
            error.set(it)
            lock.release()
        }.timeout(Duration.ofSeconds(if (timeout > 0) timeout.toLong() else 30))

        log.debug("waiting for invoke-ask result...")
        lock.acquireUninterruptibly()

        if (error.get() == null) {
            return result.get()
        } else {
            when (val e = error.get()) {
                is Throwable -> throw e
                else -> throw RuntimeException(e.toString())
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

    private fun findRegisteredAgent(agentId: String?, action: String?, stream: String?): String? {
        return registeredAgents.values.asSequence()
            .filter { agt -> agentId == null || agt.agentId == agentId }
            .filter { agt -> action == null || agt.actions.any { act -> act.name == action } }
            .filter { agt -> stream == null || agt.streams.any { str -> str.name == stream } }
            // TODO also check action parameters?
            .map { it.agentId }
            .firstOrNull()
    }

    private fun getParameters(): Map<String, String> {
        return image.parameters
            .filter { !it.isConfidential }
            .associate { Pair(it.name, System.getenv().getOrDefault(it.name, it.defaultValue)) }
    }

    fun registerErrorCode(exceptionClass: Class<out Exception>, code: Int) {
        server.registerErrorCode(exceptionClass, code)
    }

}
