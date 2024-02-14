package de.gtarc.opaca.sample

import de.dailab.jiacvi.behaviour.act
import de.gtarc.opaca.api.AgentContainerApi
import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.model.Message
import de.gtarc.opaca.model.Parameter
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

class SampleAgent(name: String): AbstractContainerizedAgent(name=name) {

    private var lastMessage: Any? = null
    private var lastBroadcast: Any? = null
    private var lastPostedStream: Any? = null

    override fun preStart() {
        super.preStart()

        addAction("DoThis", mapOf(
            "message" to Parameter("string", true),
            "sleep_seconds" to Parameter("integer", true)
        ), "string") {
            actionDoThis(it["message"]!!.asText(), it["sleep_seconds"]!!.asInt())
        }
        addAction("GetInfo", mapOf(), "object") {
            actionGetInfo()
        }
        addAction("GetEnv", mapOf(), "object") {
            actionGetEnv()
        }
        addAction("Add", mapOf(
            "x" to Parameter("integer", true),
            "y" to Parameter("integer", true)
        ), "integer") {
            actionAdd(it["x"]!!.asInt(), it["y"]!!.asInt())
        }
        addAction("Fail", mapOf(), "null") {
            actionFail()
        }
        addAction("CreateAction", mapOf(
            "name" to Parameter("string", true),
            "notify" to Parameter("boolean", false)
        ), "null") {
            createAction(it["name"]!!.asText(), it["notify"]?.asBoolean() ?: true)
        }
        addAction("SpawnAgent", mapOf(
            "name" to Parameter("string", true)
        ), "null") {
            spawnAgent(it["name"]!!.asText())
        }
        addAction("Deregister", mapOf(), "null") {
            deregister(false)
            stop()
        }
        addAction("ValidatorTest", mapOf(
            "car" to Parameter("Car", true),
            "listOfLists" to Parameter("array", true,
                Parameter.ArrayItems("array", Parameter.ArrayItems("integer", null))),
            "decimal" to Parameter("number", false),
            "desk" to Parameter("Desk", false)
        ), "string") {
            val carText = "Parameter \"car\": ${it["car"]!!.asText()}"
            val listText = "Parameter \"listOfLists\"${it["listOfLists"]!!.asText()}"
            val result = "ValidatorTest:\n$carText\n$listText"
            print(result)
            result
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

    private fun actionDoThis(message: String, sleepSeconds: Int): String {
        log.info("in 'DoThis' action, waiting...")
        println(message)
        Thread.sleep(1000 * sleepSeconds.toLong())
        log.info("done waiting")
        return "Action 'DoThis' of $name called with message=$message and sleep_seconds=$sleepSeconds"
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
        addAction(name, mapOf(), "string") {
            "Called extra action $name"
        }
        register(notify)
    }

    private fun spawnAgent(name: String) {
        system.spawnAgent(SampleAgent(name))
    }

}
