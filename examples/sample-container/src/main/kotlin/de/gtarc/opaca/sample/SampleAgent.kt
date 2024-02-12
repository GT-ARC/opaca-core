package de.gtarc.opaca.sample

import de.gtarc.opaca.api.AgentContainerApi
import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.model.Stream
import de.gtarc.opaca.model.Message
import de.dailab.jiacvi.behaviour.act

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException

class SampleAgent(name: String): AbstractContainerizedAgent(name=name) {

    private var lastMessage: Any? = null
    private var lastBroadcast: Any? = null
    private var lastPostedStream: Any? = null

    override fun preStart() {
        super.preStart()

        addAction("DoThis", mapOf("message" to "String", "sleep_seconds" to "Int"), "String") {
            actionDoThis(it["message"]!!.asText(), it["sleep_seconds"]!!.asInt())
        }
        addAction("GetInfo", mapOf(), "Map") {
            actionGetInfo()
        }
        addAction("GetEnv", mapOf(), "Map") {
            actionGetEnv()
        }
        addAction("Add", mapOf("x" to "String", "y" to "Int"), "Int") {
            actionAdd(it["x"]!!.asInt(), it["y"]!!.asInt())
        }
        addAction("Fail", mapOf(), "void") {
            actionFail()
        }
        addAction("CreateAction", mapOf("name" to "String", "notify" to "Boolean"), "void") {
            createAction(it["name"]!!.asText(), it["notify"]!!.asBoolean())
        }
        addAction("SpawnAgent", mapOf("name" to "String"), "void") {
            spawnAgent(it["name"]!!.asText())
        }
        addAction("Deregister", mapOf(), "void") {
            deregister(false)
            stop()
        }
        addAction("ErrorTest", mapOf("hint" to "String"), "String") {
            val hint = it["hint"]!!.asText()
            actionErrorTest(hint)
        }

        addStreamPost("PostStream", this::actionPostStream)
        addStreamGet("GetStream", this::actionGetStream)
    }

    override fun behaviour() = super.behaviour().and(act {

        on<Message> {
            log.info("ON $it")
            lastMessage = it.payload
        }

        listen<Message>("topic") {
            log.info("LISTEN $it")
            lastBroadcast = it.payload
        }
    })

    private fun actionGetStream(): ByteArrayInputStream {
        val data = "{\"key\":\"value\"}".toByteArray(Charset.forName("UTF-8"))
        return ByteArrayInputStream(data)
    }

    private fun actionPostStream(inputStream: ByteArray) {
        // TODO shouldn't this get an InputStream as input, and not a ByteArray?
        val content = ByteArrayInputStream(inputStream).reader().readLines()
        lastPostedStream = content
    }

    private fun actionDoThis(message: String, sleep_seconds: Int): String {
        log.info("in 'DoThis' action, waiting...")
        println(message)
        Thread.sleep(1000 * sleep_seconds.toLong())
        log.info("done waiting")
        return "Action 'DoThis' of $name called with message=$message and sleep_seconds=$sleep_seconds"
    }

    private fun actionAdd(x: Int, y: Int) = x + y

    private fun actionFail() {
        throw RuntimeException("Action Failed (as expected)")
    }

    private fun actionGetInfo() = mapOf(
        Pair("name", name),
        Pair("lastMessage", lastMessage),
        Pair("lastBroadcast", lastBroadcast),
        Pair("lastPostedStream", lastPostedStream),
        Pair(AgentContainerApi.ENV_CONTAINER_ID, System.getenv(AgentContainerApi.ENV_CONTAINER_ID)),
        Pair(AgentContainerApi.ENV_PLATFORM_URL, System.getenv(AgentContainerApi.ENV_PLATFORM_URL)),
        Pair(AgentContainerApi.ENV_OWNER, System.getenv(AgentContainerApi.ENV_OWNER)),
        Pair(AgentContainerApi.ENV_TOKEN, System.getenv(AgentContainerApi.ENV_TOKEN))
    )

    private fun actionGetEnv() = System.getenv()

    private fun createAction(name: String, notify: Boolean) {
        addAction(name, mapOf(), "String") {
            "Called extra action $name"
        }
        register(notify)
    }

    private fun spawnAgent(name: String) {
        system.spawnAgent(SampleAgent(name))
    }

    private fun actionErrorTest(hint: String): String {
        return when (hint) {
            "no-error" -> "no error"
            "not-found-error" -> throw NoSuchElementException("does not exist")
            "io-error" -> throw IOException("io exception")
            "runtime-error" -> throw RuntimeException("some runtime error", RuntimeException("cause"))
            else -> "default"
        }
    }

}
