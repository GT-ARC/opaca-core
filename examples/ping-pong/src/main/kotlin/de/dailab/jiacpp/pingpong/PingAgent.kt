package de.dailab.jiacpp.pingpong

import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.NumericNode
import de.dailab.jiacpp.container.*
import de.dailab.jiacpp.model.Action
import de.dailab.jiacpp.model.AgentDescription
import de.dailab.jiacpp.model.Message
import de.dailab.jiacpp.util.RestHelper
import de.dailab.jiacvi.behaviour.act
import java.time.Duration
import kotlin.random.Random


class PingAgent: AbstractContainerizedAgent(name="ping-agent") {

    override fun getDescription() = AgentDescription(
        this.name,
        this.javaClass.name,
        listOf()
    )

    var lastRequest = -1;
    val offers = mutableMapOf<String, Int>()

    override fun behaviour() = act {

        every(Duration.ofSeconds(5)) {
            // if already collected messages from last turn, invoke action at best Pong agent
            if (offers.isNotEmpty()) {
                val best = offers.entries.maxBy { it.value }!!
                val invoke = OutboundInvoke("PongAction", best.key, mapOf(
                    Pair("request", IntNode(lastRequest)),
                    Pair("offer", IntNode(best.value))
                ))
                offers.clear()

                // send invoke to container agent
                log.info("INVOKING ACTION FOR REQUEST $lastRequest AT BEST $best: $invoke")
                val ref = system.resolve(CONTAINER_AGENT)
                ref invoke ask<String>(invoke) {
                    log.info("RESULT TO INVOKE: $it")
                }
            }
            // send new request message to all Pong agents
            lastRequest = Random.nextInt()
            val broadcast = OutboundBroadcast("pong-channel", PingMessage(lastRequest), name)
            log.info("SENDING NEW REQUEST $lastRequest: $broadcast")
            val ref = system.resolve(CONTAINER_AGENT)
            ref tell broadcast
        }

        on<Message> {
            log.info("ON $it")
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
