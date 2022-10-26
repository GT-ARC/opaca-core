package de.dailab.jiacpp.pingpong


data class PingMessage(val request: Int)

data class PongMessage(val request: Int, val agentId: String, val offer: Int)
