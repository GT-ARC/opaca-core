package de.gtarc.opaca.pingpong

// in order to work with Jackson (JSON ser/deser) the attributes have to be variable and have a default value
// (i.e. there has to be a default constructor)

data class PingMessage(
    var request: Int = 0
)

data class PongMessage(
    var request: Int = 0,
    var agentId: String = "",
    var offer: Int = 0
)
