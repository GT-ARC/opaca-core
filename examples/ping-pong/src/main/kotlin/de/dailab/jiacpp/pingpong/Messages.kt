package de.dailab.jiacpp.pingpong

// TODO there seem to be some problems with Kotlin data classes and Jackson JSON;
//  for now, use src/main/java/de/dailab/jiacpp/pingpong/Messages.java

data class PingMessage_Kotlin(val request: Int)

data class PongMessage_Kotlin(val request: Int, val agentId: String, val offer: Int)
