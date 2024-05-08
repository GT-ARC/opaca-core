package de.gtarc.opaca.test

import de.dailab.jiacvi.behaviour.act
import de.dailab.jiacvi.communication.LocalBroker
import de.dailab.jiacvi.dsl.agentSystem
import de.dailab.jiacvi.platform.sleep
import de.gtarc.opaca.container.ContainerAgent
import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.container.Invoke
import de.gtarc.opaca.util.ConfigLoader
import de.gtarc.opaca.util.RestHelper
import de.gtarc.opaca.model.AgentContainerImage
import de.gtarc.opaca.model.Parameter
import de.gtarc.opaca.test.NUM_CLIENTS
import java.time.Duration
import com.fasterxml.jackson.databind.JsonNode

val NUM_CLIENTS: Int = 10
val TURN_DURATION: Long = 2000
val USE_OPACA = true
val ACTION_SLEEP: Long = 0

fun main() {
    agentSystem("test") {
        enable(LocalBroker)
        agents {
            add(ContainerAgent(AgentContainerImage()))
            add(ServerAgent("server"))
            for (i in 1..NUM_CLIENTS) {
                add(ClientAgent("client_$i", i))
            }
        }
    }.start()
}

class ClientAgent(name: String, val id: Int): AbstractContainerizedAgent(name=name) {
    var turn = 1
    var tick = 0
    
    override fun behaviour() = act {
        every(Duration.ofMillis(TURN_DURATION / NUM_CLIENTS)) {
            log.info("$name executing in turn $turn")
            if (tick % NUM_CLIENTS == id / 2) {
                if (USE_OPACA)
                    opacaSyncInvoke("SendTurn", "server", mapOf("id" to name, "turn" to turn))
                else
                    jiacviAsyncInvoke("SendTurn", "server", mapOf("id" to name, "turn" to turn))
                turn++
            }
            tick++
        }
    }
    
    fun opacaSyncInvoke(action: String, agent: String, parameters: Map<String, Any>) {
        val res = sendOutboundInvoke(action, agent, parameters, String::class.java, 1)
        log.info("result is $res")
    }
    
    fun jiacviAsyncInvoke(action: String, agent: String, parameters: Map<String, Any>) {
        // no problems here --> not related to JIAC VI but to OPACA
        val jsonParameters = parameters.entries.associate { Pair<String, JsonNode>(it.key, RestHelper.mapper.valueToTree(it.value)) }
        val request = Invoke(action, jsonParameters)
        val ref = system.resolve(agent)
        log.info("asking (thread ${Thread.currentThread().name})")
        ref invoke ask<String>(request) {
            log.info("result is $it (thread ${Thread.currentThread().name})")
        }.error {
            log.info("ERROR $it")
        }.timeout(Duration.ofSeconds(1))
    }
}

class ServerAgent(name: String): AbstractContainerizedAgent(name=name) {
    val responses = mutableMapOf<String, Int>()

    override fun preStart() {
        super.preStart()
        addAction("SendTurn", mapOf("id" to Parameter("string"), "turn" to Parameter("int")), Parameter("String")) {
            log.info("server got $it (thread ${Thread.currentThread().name})")
            val id = it["id"]!!.asText()
            val turn = it["turn"]!!.asInt()
            responses[id] = turn
            sleep(ACTION_SLEEP)
            "$id $turn"
        }
    }

    override fun behaviour() = super.behaviour().and(act {
        every(Duration.ofMillis(TURN_DURATION)) {
            log.info("$name executing (thread ${Thread.currentThread().name})")
            log.info("RESPONSES: ${responses.size} $responses")
            //responses.clear()
        }
    })
}
