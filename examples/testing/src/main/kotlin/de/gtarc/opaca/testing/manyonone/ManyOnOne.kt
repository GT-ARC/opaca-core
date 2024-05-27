package de.gtarc.opaca.testing.manyonone

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
import java.time.Duration
import com.fasterxml.jackson.databind.JsonNode

/*
 * BACKGROUND: There was a hard to reproduce error (#108) where sometimes an action would be executed,
 * but the callback in the ContainerAgent is never called and the result not propagated back to the
 * caller. Later, in preparation to the AOT exercise, this popped up again when a large-ish number of
 * clients simultaneously sends "invoke" requests to a server. 
 * 
 * WHAT THIS DOES: This starts one "server" and a variable number of "clients". Each turn, the clients
 * will invoke an action of the server, and the server will simply keep track when was the last time
 * each client called the action. Clients can all invoke the action at the same time, or in "batches".
 * 
 * WHAT TO LOOK OUT FOR: All clients should be "alive", invoking the action in each turn. Consequently,
 * in the server's output, in the line starting with "RESPONSES", there should be "NUM_CLIENTS" answers
 * in each turn (the first number) and for each client, the turn the last message was received should
 * be the same (or at most one removed). Make sure the parameters work out, e.g. NUM_CLIENTS being a
 * multiple of BATCH_SIZE (both can be the same) and the ACTION_SLEEP being not too long so all can be
 * handled in each turn.
 * 
 * RELATED ISSUE OR MERGE REQUEST: https://gitlab.dai-labor.de/jiacpp/prototype/-/merge_requests/107
 */

val NUM_CLIENTS: Int = 10
val TURN_DURATION: Long = 1000
val USE_OPACA = true
val ACTION_SLEEP: Long = 70
val TIMEOUT_SEC = 10
val BATCH_SIZE = 2

fun runManyOnOneTest() {
    val name = "testing"
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