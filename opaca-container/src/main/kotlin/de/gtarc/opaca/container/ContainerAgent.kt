package de.gtarc.opaca.container

import com.fasterxml.jackson.databind.JsonNode
import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.BrokerAgentRef
import de.dailab.jiacvi.behaviour.act
import de.gtarc.opaca.api.AgentContainerApi
import de.gtarc.opaca.model.*
import de.gtarc.opaca.util.ApiProxy
import de.gtarc.opaca.util.RestHelper
import de.gtarc.opaca.util.WebSocketConnector
import java.io.InputStream
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.Semaphore


const val CONTAINER_AGENT = "container-agent"

/**
 * Agent providing the REST interface of the Agent Container using a simple Jetty server.
 * The API is not quite as fancy one provided using Spring Boot or similar, but might be
 * sufficient, since all "outside" calls would go through the Runtime Container.
 */
class ContainerAgent(
        val image: AgentContainerImage, 
        val subscribeToEvents: Boolean = false
    ): Agent(overrideName=CONTAINER_AGENT) {

    private val broker by resolve<BrokerAgentRef>()

    private val server by lazy { RestServerJavalin(impl, image.apiPort, token) }

    // information on current state of agent container

    /** when the Agent Container was initialized */
    private val startedAt = ZonedDateTime.now(ZoneId.of("Z"))

    /** the ID of the Agent Container itself, received on initialization */
    private val containerId = System.getenv(AgentContainerApi.ENV_CONTAINER_ID)

    /** the URL of the parent Runtime Platform, received on initialization */
    private val runtimePlatformUrl = System.getenv(AgentContainerApi.ENV_PLATFORM_URL)

    /** the token for accessing the parent Runtime Platform, received on initialization */
    private var token = System.getenv(AgentContainerApi.ENV_TOKEN)

    /** API Proxy for sending request to this ContainerAgent's parent RuntimePlatform */
    private var parentProxy: ApiProxy = ApiProxy(runtimePlatformUrl, containerId, token)

    /** the owner who started the Agent Container */
    private val owner = System.getenv(AgentContainerApi.ENV_OWNER)

    /** other agents registered at the container agent (not all agents are exposed automatically) */
    private val registeredAgents = mutableMapOf<String, AgentDescription>()


    /**
     * Start web server (with delay to allow Agents to initialize first)
     */
    override fun preStart() {
        super.preStart()
        Thread {
            Thread.sleep(1000)
            log.info("Starting Container Agent...")
            server.start()
            if (subscribeToEvents) {
                WebSocketConnector.subscribe(runtimePlatformUrl, token, "/invoke", this::onEvent)
            }
        }.start()
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
     * Callback for the /subscribe websocket, reacting on events from the runtime platform
     */
    fun onEvent(message: String) {
        log.debug("WEBSOCKET EVENT: $message")
        val event = RestHelper.readObject(message, Event::class.java)
        val action = Regex("invoke/(\\w+)").find(event.route)?.groupValues?.get(1)
        if (action != null) {
            broker.publish(action, event)
        }
    }

    /**
     * Implementation of the Agent Container API. The methods of this class are executed by the
     * HTTP handler in its thread. It acts as bridge between HTTP handler and Container Agent.
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
            log.debug("SEND: {} {}", agentId, message)
            val agent = findRegisteredAgent(agentId, action=null, stream=null)
            val ref = system.resolve(agent)
            ref tell message
        }

        override fun broadcast(channel: String, message: Message, containerId: String, forward: Boolean) {
            log.debug("BROADCAST: {} {}", channel, message)
            broker.publish(channel, message)
        }

        override fun invoke(action: String, parameters: Map<String, JsonNode>, agentId: String?, timeout: Int, containerId: String, forward: Boolean): JsonNode? {
            log.debug("INVOKE ACTION OF AGENT: {} {} {}", agentId, action, parameters)
            val agent = findRegisteredAgent(agentId, action, null)
            val res: Any = waitForInvoke(agent, Invoke(action, parameters), timeout)
            return RestHelper.mapper.valueToTree(res)
        }

        override fun postStream(stream: String, data: ByteArray, agentId: String?, containerId: String, forward: Boolean) {
            log.debug("POST STREAM TO AGENT: $agentId $stream")
            val agent = findRegisteredAgent(agentId, null, stream)
            waitForInvoke(agent, StreamPost(stream, data), -1)
        }

        override fun getStream(stream: String, agentId: String?, containerId: String, forward: Boolean): InputStream? {
            log.debug("GET STREAM OF AGENT: $agentId $stream")
            val agent = findRegisteredAgent(agentId, null, stream)
            val inputStream: InputStream = waitForInvoke(agent, StreamGet(stream), -1) as InputStream
            return inputStream
        }

        private fun waitForInvoke(agentId: String, request: Any, timeout: Int): Any {
            // send pending invoke to ContainerAgent to execute asynchronously
            val pendInv = PendingInvoke(agentId, request, timeout, Semaphore(0), null, null)
            self tell pendInv
            pendInv.lock.acquireUninterruptibly()
            // wait for lock to be released by ContainerAgent, then...
            return when {
                pendInv.result != null -> pendInv.result!!
                pendInv.error is Throwable -> throw pendInv.error as Throwable
                else -> throw RuntimeException(pendInv.error.toString())
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

            // put next actions into own message queue instead of performing the action immediately
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

        // get pending incoming invoke from HTTP handler and execute it, notify HTTP handler via semaphore
        on<PendingInvoke> {
            val ref = system.resolve(it.agentId)
            ref invoke ask<Any>(it.request) { res ->
                log.info("RESULT $res")
                it.result = res
                it.lock.release()
            }.error { err ->
                log.warn("ERROR $err")
                it.error = err
                it.lock.release()
            }.timeout(Duration.ofSeconds(if (it.timeout > 0) it.timeout.toLong() else 30))
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
        if (! server.isRunning) return
        try {
            parentProxy.notifyUpdateContainer(containerId)
        } catch (e: RestHelper.RequestException) {
            log.error("Failed to notify parent platform: ${e.message}")
        }
    }

    private fun renewToken() {
        token = parentProxy.renewToken()
        parentProxy = ApiProxy(runtimePlatformUrl, containerId, token)
        for (agentId in registeredAgents.keys.toList()) {
            val ref = system.resolve(agentId)
            ref tell RenewToken(token!!)
        }
    }

    /*
     * actually this method is also called only by the HTTP handler, but it's fast-running
     * and might also  be useful for the ContainerAgent itself, so I will leave his here...
     */
    private fun findRegisteredAgent(agentId: String?, action: String?, stream: String?): String {
        return registeredAgents.values.asSequence()
            .filter { agt -> agentId == null || agt.agentId == agentId }
            .filter { agt -> action == null || agt.actions.any { act -> act.name == action } }
            .filter { agt -> stream == null || agt.streams.any { str -> str.name == stream } }
            // TODO also check action parameters?
            .map { it.agentId }
            .ifEmpty { 
                throw NoSuchElementException(when {
                    action != null -> "Action $action of Agent $agentId not found"
                    stream != null -> "Stream $stream of Agent $agentId not found"
                    else -> "Agent $agentId not found"
                })
             }
            .first()
    }

    private fun getParameters(): Map<String, String> {
        return image.parameters
            .filter { !it.isConfidential }
            .associate { Pair(it.name, System.getenv().getOrDefault(it.name, it.defaultValue)) }
    }

    // purely internal message sent from the HTTP handler to the ContainerAgent
    private data class PendingInvoke(
        // information on what to invoke
        val agentId: String,
        val request: Any,
        val timeout: Int,
        // (waiting for) the result
        val lock: Semaphore,
        var result: Any?,
        var error: Any?
    )

}
