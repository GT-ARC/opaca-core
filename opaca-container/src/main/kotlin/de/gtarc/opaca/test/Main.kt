package de.gtarc.opaca.test

import de.dailab.jiacvi.behaviour.act
import de.dailab.jiacvi.communication.LocalBroker
import de.dailab.jiacvi.dsl.agentSystem
import de.gtarc.opaca.container.ContainerAgent
import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.container.Invoke
import de.gtarc.opaca.util.ConfigLoader
import de.gtarc.opaca.util.RestHelper
import de.gtarc.opaca.model.AgentContainerImage
import de.gtarc.opaca.model.Parameter
import java.time.Duration
import com.fasterxml.jackson.databind.JsonNode

fun main() {
    agentSystem("opaca-sample-container") {
        enable(LocalBroker)
        agents {
            add(ContainerAgent(AgentContainerImage()))
            add(ServerAgent("server"))
            for (i in 1..10) {
                add(ClientAgent("client_$i"))
            }
        }
    }.start()
}

class ClientAgent(name: String): AbstractContainerizedAgent(name=name) {
    var turn = 0
    override fun behaviour() = act {
        every(Duration.ofMillis(200)) {
            log.info("$name executing in turn $turn")
            opacaSyncInvoke("SendTurn", "server", mapOf("id" to name, "turn" to turn))
            //jiacviAsyncInvoke("SendTurn", "server", mapOf("id" to name, "turn" to turn))
        }
    }
    fun opacaSyncInvoke(action: String, agent: String, parameters: Map<String, Any>) {
        val res = sendOutboundInvoke(action, agent, parameters, String::class.java)
        log.info("result is $res")
        turn++
    }
    fun jiacviAsyncInvoke(action: String, agent: String, parameters: Map<String, Any>) {
        // no problems here --> not related to JIAC VI but to OPACA
        val jsonParameters = parameters.entries.associate { Pair<String, JsonNode>(it.key, RestHelper.mapper.valueToTree(it.value)) }
        val request = Invoke(action, jsonParameters)
        val ref = system.resolve(agent)
        ref invoke ask<String>(request) {
            log.info("result is $it")
            turn++
        }
    }
}

class ServerAgent(name: String): AbstractContainerizedAgent(name=name) {
    val responses = mutableMapOf<String, Int>()

    override fun preStart() {
        super.preStart()
        addAction("SendTurn", mapOf("id" to Parameter("string"), "turn" to Parameter("int")), Parameter("String")) {
            log.info("server got $it")
            val id = it["id"]!!.asText()
            val turn = it["turn"]!!.asInt()
            responses[id] = turn
            "$id $turn"
        }
    }

    override fun behaviour() = super.behaviour().and(act {
        every(Duration.ofMillis(200)) {
            log.info("$name executing")
            log.info("RESPONSES: ${responses.size} $responses")
            responses.clear()
        }
    })
}
