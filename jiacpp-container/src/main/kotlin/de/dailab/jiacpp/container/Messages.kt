package de.dailab.jiacpp.container

import com.fasterxml.jackson.databind.JsonNode

// TODO placeholders for messages to be forwarded by container agent to platform (and thus to
//  other containers, possibly connected platforms, etc.)


// TODO dedicated messages for register? deregister?

data class Invoke(val name: String, val parameters: Map<String, JsonNode>)
