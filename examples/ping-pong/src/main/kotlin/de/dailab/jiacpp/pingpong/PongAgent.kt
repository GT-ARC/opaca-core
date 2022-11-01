package de.dailab.jiacpp.pingpong

import de.dailab.jiacpp.container.AbstractContainerizedAgent
import de.dailab.jiacpp.container.CONTAINER_AGENT
import de.dailab.jiacpp.container.Invoke
import de.dailab.jiacpp.container.OutboundMessage
import de.dailab.jiacpp.model.Action
import de.dailab.jiacpp.model.AgentDescription
import de.dailab.jiacpp.model.Message
import de.dailab.jiacpp.util.RestHelper
import de.dailab.jiacvi.behaviour.act
import kotlin.random.Random
import kotlin.random.nextInt


class PongAgent: AbstractContainerizedAgent(name="pong-agent-${Random.nextInt()}") {

    override fun getDescription() = AgentDescription(
        this.name,
        this.javaClass.name,
        listOf(
            Action("PongAction", mapOf(Pair("request", "Int"), Pair("offer", "Int")), "String")
        )
    )

    override fun behaviour() = act {

        listen<Message>("pong-channel") {
            log.info("LISTEN $it")
            // listen to ping message, send offer to ping agent
            log.info("LISTEN RECEIVED $it")
            val ping = RestHelper.mapper.convertValue(it.payload, Messages.PingMessage_Java::class.java)
            val offer = Random.nextInt(0, 1000)
            val pong = Messages.PongMessage_Java(ping.request, name, offer)

            sendOutboundMessage(it.replyTo, pong)
        }

        respond<Invoke, Any?> {
            log.info("RESPOND $it")
            when (it.name) {
                "PongAction" -> pongAction(it.parameters["request"]!!.asInt(), it.parameters["offer"]!!.asInt())
                else -> null
            }
        }

    }

    private fun pongAction(request: Int, offer: Int): String {
        log.info("Invoked my action for request: $request, offer: $offer")
        return "Executed request $request for offer $offer"
    }

}
