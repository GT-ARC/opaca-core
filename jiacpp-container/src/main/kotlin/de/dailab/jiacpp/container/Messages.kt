package de.dailab.jiacpp.container

import com.fasterxml.jackson.databind.JsonNode

// TODO placeholders for messages to be forwarded by container agent to platform (and thus to
//  other containers, possibly connected platforms, etc.)


// TODO register, deregister
//  outbound invoke, message, broadcast
//  internal invoke (message, broadcast should not be needed)


data class OutboundInvoke(val name: String, val agentId: String?, val parameters: Map<String, JsonNode>)

data class OutboundMessage(val agentId: String, val message: Any, val ownId: String)

data class OutboundBroadcast(val channel: String, val message: Any, val ownId: String)

data class Invoke(val name: String, val parameters: Map<String, JsonNode>)
