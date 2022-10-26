package de.dailab.jiacpp.container

import de.dailab.jiacpp.model.AgentDescription
import de.dailab.jiacvi.Agent


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

    override fun preStart() {
        super.preStart()
        register()
    }

    fun register() {
        val desc = getDescription()
        val ref = system.resolve(CONTAINER_AGENT)
        ref invoke ask<Boolean>(desc) {
            log.info("REGISTERED: $it")
        }
    }

    abstract fun getDescription(): AgentDescription

}
