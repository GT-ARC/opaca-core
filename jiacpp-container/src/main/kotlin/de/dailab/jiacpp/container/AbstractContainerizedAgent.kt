package de.dailab.jiacpp.container

import com.fasterxml.jackson.databind.JsonNode
import de.dailab.jiacpp.model.AgentDescription
import de.dailab.jiacpp.model.Message
import de.dailab.jiacpp.util.ApiProxy
import de.dailab.jiacpp.util.RestHelper
import de.dailab.jiacvi.Agent
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread


/**
 * Abstract superclass for containerized agents, handling the registration with the container agent.
 * May also provide additional helper methods, e.g. for sending outbound messages.
 *
 * Not each agent in the AgentContainer has to extend this class and register with the ContainerAgent.
 * There are three types of agents in the agent container:
 * - exactly one ContainerAgent handling the communication with the RuntimePlatform
 * - one or more ContainerizedAgents, registers with the ContainerAgent, provides actions and/or reacts to messages
 * - zero or more regular agents that may interact with the ContainerizedAgents or just perform background tasks
 */
abstract class AbstractContainerizedAgent(name: String): Agent(overrideName=name) {

    /** proxy to parent Runtime Platform for forwarding outgoing calls */
    private var runtimePlatformUrl: String? = null
    private val parentProxy: ApiProxy by lazy { ApiProxy(runtimePlatformUrl) }

    override fun preStart() {
        super.preStart()
        register()
    }

    fun register() {
        val desc = getDescription()
        val ref = system.resolve(CONTAINER_AGENT)
        ref invoke ask<String?>(desc) {
            log.info("REGISTERED: Parent URL is $it")
            runtimePlatformUrl = it
        }
    }

    abstract fun getDescription(): AgentDescription


    fun <T> sendOutboundInvoke(action: String, agentId: String?, parameters: Map<String, Any?>, type: Class<T>): T {
        log.info("Outbound Invoke: $action @ $agentId ($parameters)")
        val jsonParameters: Map<String, JsonNode> = parameters.entries
            .associate { Pair<String, JsonNode>(it.key, RestHelper.mapper.valueToTree(it.value)) }
        val res = when (agentId) {
            null -> parentProxy.invoke(action, jsonParameters)
            else -> parentProxy.invoke(agentId, action, jsonParameters)
        }
        log.info("invoke result: $res")
        return RestHelper.mapper.treeToValue(res, type)
    }

    fun sendOutboundBroadcast(channel: String, message: Any) {
        log.info("Outbound Broadcast: $message @ $channel")
        val payload: JsonNode = RestHelper.mapper.valueToTree(message)
        parentProxy.broadcast(channel, Message(payload, name))
    }

    fun sendOutboundMessage(agentId: String, message: Any) {
        log.info("Outbound Message: $message @ $agentId")
        val payload: JsonNode = RestHelper.mapper.valueToTree(message)
        parentProxy.send(agentId, Message(payload, name))
    }

}
