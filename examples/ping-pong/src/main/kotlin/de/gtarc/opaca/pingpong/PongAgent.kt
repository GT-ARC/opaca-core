package de.gtarc.opaca.pingpong

import de.dailab.jiacvi.behaviour.act
import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.model.Message
import de.gtarc.opaca.model.Parameter
import de.gtarc.opaca.util.RestHelper
import kotlin.random.Random


class PongAgent: AbstractContainerizedAgent(name="pong-agent-${Random.nextInt()}") {

    override fun setupAgent() {
        addAction("PongAction", mapOf(
            "request" to Parameter("integer", true),
            "offer" to Parameter("integer", true)
        ), Parameter("string", true)) {
            pongAction(it["request"]!!.asInt(), it["offer"]!!.asInt())
        }
    }

    override fun behaviour() = super.behaviour().and(act {

        listen<Message>("pong-channel") {
            // listen to ping message, send offer to ping agent
            val ping = RestHelper.mapper.convertValue(it.payload, PingMessage::class.java)
            log.info("Received Ping $ping")

            val offer = Random.nextInt(0, 1000)
            val pong = PongMessage(ping.request, name, offer)
            log.info("Sending Pong $pong")
            sendOutboundMessage(it.replyTo, pong)
        }

    })

    private fun pongAction(request: Int, offer: Int): String {
        log.info("Invoked my action for request: $request, offer: $offer")
        return "Executed request $request for offer $offer"
    }

}
