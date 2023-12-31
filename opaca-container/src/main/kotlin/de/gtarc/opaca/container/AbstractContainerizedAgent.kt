package de.gtarc.opaca.container

import com.fasterxml.jackson.databind.JsonNode
import de.gtarc.opaca.model.Action

import de.gtarc.opaca.model.AgentDescription
import de.gtarc.opaca.model.Message
import de.gtarc.opaca.model.Stream
import de.gtarc.opaca.util.ApiProxy
import de.gtarc.opaca.util.RestHelper
import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.behaviour.act

import org.springframework.http.ResponseEntity
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody

/**
 * Abstract superclass for containerized agents, handling the registration with the container agent.
 * It also provides helper methods for outbound communication via the RuntimePlatform. The latter is
 * handled by the agents themselves, not via the ContainerAgent, since otherwise in particular invoke
 * would block its thread for a long time, thus blocking all other communication, possibly deadlocking.
 *
 * Not each agent in the AgentContainer has to extend this class and register with the ContainerAgent.
 * There are three types of agents in the agent container:
 * - exactly one ContainerAgent forwarding messages from the RuntimePlatform to the respective agents
 * - one or more ContainerizedAgents, registers with the ContainerAgent, provides actions and/or reacts to messages
 * - zero or more regular agents that may interact with the ContainerizedAgents or just perform background tasks
 */
abstract class AbstractContainerizedAgent(name: String): Agent(overrideName=name) {

    /** proxy to parent Runtime Platform for forwarding outgoing calls */
    private var runtimePlatformUrl: String? = null
    private var token: String? = null
    private val parentProxy: ApiProxy by lazy { ApiProxy(runtimePlatformUrl, token) }

    protected val actions = mutableListOf<Action>()
    protected val actionCallbacks = mutableMapOf<String, (Map<String, JsonNode>) -> Any?>()
    protected val streams = mutableListOf<Stream>()
    protected val streamCallbacks = mutableMapOf<String, () -> Any?>()

    override fun preStart() {
        super.preStart()
        register(false)
    }

    fun register(notify: Boolean) {
        log.info("REGISTERING...")
        val desc = getDescription()
        val ref = system.resolve(CONTAINER_AGENT)
        ref invoke ask<Registered>(Register(desc, notify)) {
            log.info("REGISTERED: Parent URL is ${it.parentUrl}")
            runtimePlatformUrl = it.parentUrl
            token = it.authToken
        }
    }

    fun deregister(notify: Boolean) {
        log.info("DE-REGISTERING...")
        val desc = getDescription()
        val ref = system.resolve(CONTAINER_AGENT)
        ref tell DeRegister(desc.agentId, notify)
    }

    open fun getDescription() = AgentDescription(
        this.name,
        this.javaClass.name,
        actions,
        streams
    )

    fun addAction(name: String, parameters: Map<String, String>, result: String, callback: (Map<String, JsonNode>) -> Any?) {
        addAction(Action(name, parameters, result), callback)
    }

    fun addAction(action: Action, callback: (Map<String, JsonNode>) -> Any?) {
        log.info("Added action: $action")
        actions.add(action)
        actionCallbacks[action.name] = callback
    }

    fun addStream(name: String, mode: Stream.Mode, callback: () -> Any?) {
        addStrean(Stream(name, mode), callback)
    }

    fun addStrean(stream: Stream, callback: () -> Any?) {
        log.info("Added stream: $stream")
        streams.add(stream)
        streamCallbacks[stream.name] = callback
    }

    override fun behaviour() = act {
        respond<Invoke, Any?> {
            log.info("RESPOND $it")
            when (it.name) {
                in actionCallbacks -> actionCallbacks[it.name]?.let { cb -> cb(it.parameters) }
                else -> Unit
            }
        }

        respond<StreamInvoke, Any?> {
            log.info("STREAM RESPOND $it")
            when(it.name) {
                in streamCallbacks -> streamCallbacks[it.name]?.let { it1 -> it1() }
                else -> Unit
            }
        }
    }

    /**
     * Send invoke to another agent via the parent RuntimePlatform. While this can also be used
     * to communicate with agents in the same container, JIAC's own messaging should be used then.
     */
    fun <T> sendOutboundInvoke(action: String, agentId: String?, parameters: Map<String, Any?>, type: Class<T>): T {
        log.info("Outbound Invoke: $action @ $agentId ($parameters)")
        val jsonParameters = parameters.entries
            .associate { Pair<String, JsonNode>(it.key, RestHelper.mapper.valueToTree(it.value)) }
        val res = when (agentId) {
            null -> parentProxy.invoke(action, jsonParameters, -1, null, true)
            else -> parentProxy.invoke(action, jsonParameters, agentId, -1, null, true)
        }
        return RestHelper.mapper.treeToValue(res, type)
    }

    fun sendOutboundStreamRequest(stream: String, agentId: String?, containerId: String, forward: Boolean = true): ResponseEntity<StreamingResponseBody> {
        log.info("Outbound Stream: $stream @ $containerId")
        return when (agentId) {
            null -> parentProxy.getStream(stream, containerId, forward)
            else -> parentProxy.getStream(stream, agentId, containerId, forward)
        }
    }

    /**
     * Send broadcast to other agents via the parent RuntimePlatform. While this can also be used
     * to communicate with agents in the same container, JIAC's own messaging should be used then.
     */
    fun sendOutboundBroadcast(channel: String, message: Any) {
        log.info("Outbound Broadcast: $message @ $channel")
        val payload: JsonNode = RestHelper.mapper.valueToTree(message)
        parentProxy.broadcast(channel, Message(payload, name), null, true)
    }

    /**
     * Send message to another agent via the parent RuntimePlatform. While this can also be used
     * to communicate with agents in the same container, JIAC's own messaging should be used then.
     */
    fun sendOutboundMessage(agentId: String, message: Any) {
        log.info("Outbound Message: $message @ $agentId")
        val payload: JsonNode = RestHelper.mapper.valueToTree(message)
        parentProxy.send(agentId, Message(payload, name), null, true)
    }

}
