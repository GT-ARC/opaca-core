package de.dailab.jiacpp.pingpong

import de.dailab.jiacpp.container.*
import de.dailab.jiacpp.model.AgentDescription
import de.dailab.jiacpp.model.Message
import de.dailab.jiacpp.util.RestHelper
import de.dailab.jiacpp.model.ObjectDefinition
import de.dailab.jiacvi.behaviour.act
import java.time.Duration
import kotlin.random.Random


class PingAgent: AbstractContainerizedAgent(name="ping-agent") {

    override fun getDescription() = AgentDescription(
        this.name,
        this.javaClass.name,
        listOf()
    )

    private var lastRequest = -1
    private val offers = mutableMapOf<String, Int>()

    override fun behaviour() = act {

        every(Duration.ofSeconds(5)) {
            // if already collected messages from last turn, invoke action at best Pong agent
            if (offers.isNotEmpty()) {
                val best = offers.entries.maxBy { it.value }!!
                offers.clear()

                // send invoke to container agent
                log.info("Invoking action for request $lastRequest at $best")
                val res = sendOutboundInvoke("PongAction", best.key, mapOf(
                    Pair("request", lastRequest), Pair("offer", best.value)), String::class.java)
                log.info("Result of invoke: $res")
            }
            // send new request message to all Pong agents
            lastRequest = Random.nextInt()
            log.info("Broadcasting new request $lastRequest")
            sendOutboundBroadcast("pong-channel", PingMessage(lastRequest))
        }

        on<Message> {
            // TODO how best to check payload type? here we can just assume it's a Pong message
            //  is there a better way than to just `try` to read the JSON tree as the expected type(s)?
            val pongMessage = RestHelper.mapper.convertValue(it.payload, PongMessage::class.java)
            if (pongMessage.request == lastRequest) {
                log.info("Recording offer: $pongMessage")
                offers[pongMessage.agentId] = pongMessage.offer
            } else {
                log.info("Too Late: $pongMessage")
            }
        }

    }

}
