package de.gtarc.opaca.container

import com.fasterxml.jackson.databind.JsonNode
import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.LocalAgentRef
import de.dailab.jiacvi.behaviour.act
import de.gtarc.opaca.model.*
import de.gtarc.opaca.util.ApiProxy
import de.gtarc.opaca.util.RestHelper
import java.io.InputStream

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
    private var containerId: String? = null
    private var token: String? = null
    private lateinit var parentProxy: ApiProxy

    protected val actions = mutableListOf<Action>()
    protected val actionCallbacks = mutableMapOf<String, (Map<String, JsonNode>) -> Any?>()

    protected val streams = mutableListOf<Stream>()
    protected val streamGetCallbacks = mutableMapOf<String, () -> Any?>()
    protected val streamPostCallbacks = mutableMapOf<String, (ByteArray) -> Any?>()

    final override fun preStart() {
        super.preStart()
        setupAgent()
        register(true)
    }

    /** override in subclasses to do setup stuff, e.g. adding actions */
    open fun setupAgent() {}

    fun register(notify: Boolean) {
        log.info("REGISTERING...")
        val desc = getDescription()
        val ref = system.resolve(CONTAINER_AGENT)
        ref invoke ask<Registered>(Register(desc, notify)) {
            log.info("REGISTERED: Parent URL is ${it.parentUrl}")
            runtimePlatformUrl = it.parentUrl
            containerId = it.containerId
            token = it.authToken
            parentProxy =  ApiProxy(runtimePlatformUrl, containerId, token)
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

    fun addAction(name: String, parameters: Map<String, Parameter>, result: Parameter?, callback: (Map<String, JsonNode>) -> Any?) =
            addAction(Action(name, parameters, result), callback)
    
    fun addAction(name: String, description: String?, parameters: Map<String, Parameter>, result: Parameter?, callback: (Map<String, JsonNode>) -> Any?) =
            addAction(Action(name, description, parameters, result), callback)

    fun addAction(action: Action, callback: (Map<String, JsonNode>) -> Any?) {
        log.info("Added action: $action")
        actions.add(action)
        actionCallbacks[action.name] = callback
    }

    fun addStreamGet(name: String, callback: (() -> Any?)) = addStreamGet(name, null, callback)

    fun addStreamGet(name: String, description: String?, callback: (() -> Any?)) {
        val stream = Stream(name, Stream.Mode.GET, description)
        streams.add(stream)
        streamGetCallbacks[stream.name] = callback
    }


    fun addStreamPost(name: String, callback: ((ByteArray) -> Any?)) = addStreamPost(name, null, callback)

    fun addStreamPost(name: String, description: String?, callback: ((ByteArray) -> Any?)) {
        val stream = Stream(name, Stream.Mode.POST, description)
        streams.add(stream)
        streamPostCallbacks[stream.name] = callback
    }

    override fun behaviour() = act {
        respond<Invoke, Any?> {
            log.info("RESPOND $it")
            when (it.name) {
                in actionCallbacks -> actionCallbacks[it.name]?.let { cb -> cb(it.parameters) }
                else -> Unit
            }
        }

        on<RenewToken> {
            log.info("RENEW TOKEN $it")
            token = it.value
            parentProxy =  ApiProxy(runtimePlatformUrl, containerId, token)
        }

        respond<StreamGet, Any?> {
            log.info("STREAM RESPOND $it")
            when(it.name) {
                in streamGetCallbacks -> streamGetCallbacks[it.name]?.let { it1 -> it1() }
                else -> Unit
            }
        }

        respond<StreamPost, Any?> {
            log.info("STREAM RESPOND $it")
            when(it.name) {
                in streamPostCallbacks -> streamPostCallbacks[it.name]?.let { it1 -> it1(it.body) }
                else -> Unit
            }
        }
    }

    /**
     * Send invoke to another agent via the parent RuntimePlatform. While this can also be used
     * to communicate with agents in the same container, JIAC's own messaging should be used then.
     */
    fun <T> sendOutboundInvoke(action: String, agentId: String?, parameters: Map<String, Any?>, type: Class<T>, timeout:Int=-1): T {
        log.info("Outbound Invoke: $action @ $agentId ($parameters)")
        val jsonParameters = parameters.entries
            .associate { Pair<String, JsonNode>(it.key, RestHelper.mapper.valueToTree(it.value)) }
        val res = parentProxy.invoke(action, jsonParameters, agentId, timeout, null, true)
        return RestHelper.mapper.treeToValue(res, type)
    }

    /**
     * Send get-stream to other agents via the parent runtimePlatform.
     */
    fun sendOutboundStreamGetRequest(stream: String, agentId: String?, containerId: String, forward: Boolean = true): InputStream {
        log.info("Outbound Stream: $stream @ $containerId")
        return parentProxy.getStream(stream, agentId, containerId, forward)
    }

    /**
     * Send post-stream to other agents via the parent runtimePlatform.
     */
    fun sendOutboundStreamPostRequest(
        stream: String,
        inputStream: ByteArray,
        agentId: String?,
        containerId: String,
        forward: Boolean = true
    ) {
        log.info("Outbound Stream: $stream @ $containerId")
        parentProxy.postStream(stream, inputStream, agentId, containerId, forward)
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

    /**
     * Stop this agent.
     */
    fun stop() {
        LocalAgentRef<AbstractContainerizedAgent>(self.path, this, system.dispatcher).stop()
    }
}
