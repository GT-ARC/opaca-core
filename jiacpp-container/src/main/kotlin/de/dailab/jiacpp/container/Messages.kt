package de.dailab.jiacpp.container

import com.fasterxml.jackson.databind.JsonNode
import de.dailab.jiacpp.model.AgentDescription


// Messages for Registering an Agent with the Container Agent (or updating an already registered agent)
// and for de-registering an agent. Those just wrap one other element each, but may still be useful for
// more "meaningful" messages sent to and received by the ContainerAgent.

data class Register(val description: AgentDescription, val notify: Boolean)

data class Registered(val parentUrl: String?, val containerId: String, val authToken: String?)

data class DeRegister(val agentId: String, val notify: Boolean)

// Message for Invoking a JIAC++ action at a containerized agent, wrapping the name of the action to call
// and its parameters, to be handled by an invoke-ask "respond" handler.

data class Invoke(val name: String, val parameters: Map<String, JsonNode>)

data class StreamInvoke(val name: String)
