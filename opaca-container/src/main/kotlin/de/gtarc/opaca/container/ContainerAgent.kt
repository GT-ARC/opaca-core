package de.gtarc.opaca.container

import com.fasterxml.jackson.databind.JsonNode
import de.gtarc.opaca.api.AgentContainerApi
import de.gtarc.opaca.model.*
import de.gtarc.opaca.util.ApiProxy
import de.gtarc.opaca.util.RestHelper
import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.BrokerAgentRef
import de.dailab.jiacvi.behaviour.act
import de.dailab.jiacvi.platform.currentThread
import de.dailab.jiacvi.platform.ofMillis
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

    private val server by lazy { RestServerJavalin(impl, AgentContainerApi.DEFAULT_PORT, token) }

    // information on current state of agent container

    /** when the Agent Container was initialized */
    private val startedAt = ZonedDateTime.now(ZoneId.of("Z"))

    /** the ID of the Agent Container itself, received on initialization */
    private val containerId = System.getenv(AgentContainerApi.ENV_CONTAINER_ID)

    /** the URL of the parent Runtime Platform, received on initialization */
    private val runtimePlatformUrl = System.getenv(AgentContainerApi.ENV_PLATFORM_URL)

    /** the token for accessing the parent Runtime Platform, received on initialization */
    private var token = System.getenv(AgentContainerApi.ENV_TOKEN)

    private var parentProxy: ApiProxy = ApiProxy(runtimePlatformUrl, containerId, token)

    /** the owner who started the Agent Container */
    private val owner = System.getenv(AgentContainerApi.ENV_OWNER)

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
            log.debug("GET INFO")
            return AgentContainer(containerId, image, getParameters(), agents, owner, startedAt, null)
        }

        override fun getAgents(): List<AgentDescription> {
            log.debug("GET AGENTS")
            return registeredAgents.values.toList()
        }

        override fun getAgent(agentId: String?): AgentDescription? {
            log.debug("GET AGENT: $agentId")
            return registeredAgents[agentId]
        }

        override fun send(agentId: String, message: Message, containerId: String, forward: Boolean) {
            log.debug("SEND: $agentId $message")
            val agent = findRegisteredAgent(agentId, action=null, stream=null)
            val ref = system.resolve(agent)
            ref tell message
        }

        override fun broadcast(channel: String, message: Message, containerId: String, forward: Boolean) {
            log.debug("BROADCAST: $channel $message")
            broker.publish(channel, message)
        }

        override fun invoke(action: String, parameters: Map<String, JsonNode>, agentId: String?, timeout: Int, containerId: String, forward: Boolean): JsonNode? {
            log.debug("INVOKE ACTION OF AGENT: $agentId $action $parameters")
            val agent = findRegisteredAgent(agentId, action, null)
            val res: Any = invokeAskWait(agent, Invoke(action, parameters), timeout)
            return RestHelper.mapper.valueToTree(res)
        }

        override fun postStream(stream: String, data: ByteArray, agentId: String?, containerId: String, forward: Boolean) {
            log.debug("POST STREAM TO AGENT: $agentId $stream")
            val agent = findRegisteredAgent(agentId, null, stream)
            invokeAskWait<Any?>(agent, StreamPost(stream, data), -1)
        }

        override fun getStream(stream: String, agentId: String?, containerId: String, forward: Boolean): InputStream? {
            log.debug("GET STREAM OF AGENT: $agentId $stream")
            val agent = findRegisteredAgent(agentId, null, stream)
            val inputStream: InputStream = invokeAskWait(agent, StreamGet(stream), -1)
            return inputStream
        }
    }

    /**
     * Call "ref invoke ask" at given JIAC VI agent, but wait for result and return it.
     */
    private fun <T> invokeAskWait(agentId: String, request: Any, timeout: Int): T {
        log.info("INVOKE ASK WAIT ${Thread.currentThread().name} $request") // HTTP handler thread
        val lock = Semaphore(0)
        val result = AtomicReference<T>()
        val error = AtomicReference<Any>()
        val ref = system.resolve(agentId)
        ref invoke ask<T>(request) {
            log.info("RESULT $it in thread ${Thread.currentThread().name}")
            result.set(it)
            lock.release()
        }.error {
            log.warn("ERROR $it")
            error.set(it)
            lock.release()
        }.timeout(Duration.ofSeconds(if (timeout > 0) timeout.toLong() else 30))

        //log.info("waiting for invoke-ask result... ($request)")
        lock.acquireUninterruptibly() // HTTP handler thread hängt dann hier, deshalb kriegen die client kein result, und hängen in ihrem http request

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
            Registered(runtimePlatformUrl, containerId, token)
        }

        // in case agents want to de-register themselves before the container as a whole terminates
        on<DeRegister> {
            log.info("De-Registering ${it.agentId}")
            registeredAgents.remove(it.agentId)
            if (it.notify) {
                notifyPlatform()
            }
        }

        // renew token every 9 hours (should be valid for 10 hours)
        // (first called after one interval, not directly after startup)
        every(Duration.ofSeconds(60 * 60 * 9)) {
            // TODO test if token is close to expiring --> requires async encryption for tokens so container can check it
            if (! token.isNullOrEmpty()) {
                try {
                    log.info("Renewing token...")
                    renewToken()
                } catch (e: Exception) {
                    log.error("Error during token renewal: ${e.message}")
                }
            }
        }
    }

    private fun notifyPlatform() {
        parentProxy.notifyUpdateContainer(containerId)
    }

    private fun renewToken() {
        token = parentProxy.renewToken()
        parentProxy = ApiProxy(runtimePlatformUrl, containerId, token) 
        for (agentId in registeredAgents.keys.toList()) {
            val ref = system.resolve(agentId)
            ref tell RenewToken(token!!)
        }
    }

    private fun findRegisteredAgent(agentId: String?, action: String?, stream: String?): String {
        return registeredAgents.values.asSequence()
            .filter { agt -> agentId == null || agt.agentId == agentId }
            .filter { agt -> action == null || agt.actions.any { act -> act.name == action } }
            .filter { agt -> stream == null || agt.streams.any { str -> str.name == stream } }
            // TODO also check action parameters?
            .map { it.agentId }
            .ifEmpty { 
                throw NoSuchElementException(when {
                    action != null => "Action $action of Agent $agentId not found",
                    stream != null => "Stream $stream of Agent $agentId not found",
                    else => "Agent $agentId not found"
                })
             }
            .first()
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
