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
import de.gtarc.opaca.test.BATCH_SIZE
import java.time.Duration
import com.fasterxml.jackson.databind.JsonNode

/*
OBSERVATIONS
- everything works fine with plain JIAC VI messaging (USE_OPACA = false)
- it does not make a difference if one is running just the container without a platform, or distributed in two containers plus platform
- if clients are subdivided into batches, all sending at the same time, exactly one per batch "survices"
- it does not matter if the server is still busy with the last request, the new one will just be queues and executed later
- instead, the invoke-ask callback is never executed (neither the timeout or error once the agent hangs)
*/

val NUM_CLIENTS: Int = 10
val TURN_DURATION: Long = 1000
val USE_OPACA = true
val ACTION_SLEEP: Long = 70
val TIMEOUT_SEC = 10
val BATCH_SIZE = 2

fun main() {
    val name = "test-clients"
    val image = RestHelper.readObject("{\"imageName\": \"${name}\"}", AgentContainerImage::class.java)
    agentSystem(name) {
        enable(LocalBroker)
        agents {
            add(ContainerAgent(image))
            add(ServerAgent("server"))
            for (i in 0..<NUM_CLIENTS) {
                add(ClientAgent("client_$i", i))
            }
        }
    }.start()
}

class ClientAgent(name: String, val id: Int): AbstractContainerizedAgent(name=name) {
    var turn = 0
    var tick = 0
    
    override fun behaviour() = act {
        every(Duration.ofMillis(TURN_DURATION / (NUM_CLIENTS * 2))) {
            if (tick % (NUM_CLIENTS * 2) == id / BATCH_SIZE + 1) {
                log.info("$name executing in turn $turn")
                if (turn > 0) {
                    if (USE_OPACA)
                        opacaSyncInvoke("SendTurn", "server", mapOf("id" to name, "turn" to turn))
                    else
                        jiacviAsyncInvoke("SendTurn", "server", mapOf("id" to name, "turn" to turn))
                }
                turn++
            }
            tick++
        }
    }
    
    fun opacaSyncInvoke(action: String, agent: String, parameters: Map<String, Any>) {
        val res = sendOutboundInvoke(action, agent, parameters, String::class.java, TIMEOUT_SEC)
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
        }.timeout(Duration.ofSeconds(TIMEOUT_SEC.toLong()))
    }
}

class ServerAgent(name: String): AbstractContainerizedAgent(name=name) {
    val responses = mutableMapOf<String, Int>()
    var newResponses = 0

    override fun preStart() {
        super.preStart()
        addAction("SendTurn", mapOf("id" to Parameter("string"), "turn" to Parameter("int")), Parameter("String")) {
            log.info("server got $it (thread ${Thread.currentThread().name})")
            val id = it["id"]!!.asText()
            val turn = it["turn"]!!.asInt()
            responses[id] = turn
            newResponses++
            sleep(ACTION_SLEEP)
            "$id $turn"
        }
    }

    override fun behaviour() = super.behaviour().and(act {
        every(Duration.ofMillis(TURN_DURATION)) {
            log.info("$name executing (thread ${Thread.currentThread().name})")
            log.info("RESPONSES: ${newResponses} $responses")
            newResponses = 0
        }
    })
}
