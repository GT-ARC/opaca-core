package de.dailab.jiacpp.sample

import de.dailab.jiacpp.api.AgentContainerApi
import de.dailab.jiacpp.container.AbstractContainerizedAgent
import de.dailab.jiacpp.container.Invoke
import de.dailab.jiacpp.model.Action
import de.dailab.jiacpp.model.AgentDescription
import de.dailab.jiacpp.model.Message
import de.dailab.jiacvi.behaviour.act
import java.io.IOException
import kotlin.concurrent.thread




class DataAgent(name: String): AbstractContainerizedAgent(name=name) {

    private var lastMessage: Any? = null
    private var lastBroadcast: Any? = null

    private var extraActions = mutableListOf<Action>()

    override fun getDescription() = AgentDescription(
        this.name,
        this.javaClass.name,
        listOf(
            Action("AkquireData", mapOf(Pair("camera_id", "String"), Pair("stream_seconds", "Int")), "String"),
            Action("GetInfo", mapOf(), "Map"),
            Action("Add", mapOf(Pair("x", "String"), Pair("y", "Int")), "Int"),
            Action("Fail", mapOf(), "void"),
            // actions for testing modifying agents and actions at runtime
            Action("CreateAction", mapOf(Pair("name", "String"), Pair("notify", "Boolean")), "void"),
            Action("SpawnAgent", mapOf(Pair("name", "String")), "void"),
            Action("Deregister", mapOf(), "void")
        ).plus(extraActions)
    )

    override fun behaviour() = act {

        on<Message> {
            log.info("ON $it")
            lastMessage = it.payload
        }

        listen<Message>("topic") {
            log.info("LISTEN $it")
            lastBroadcast = it.payload
        }

        respond<Invoke, Any?> {
            log.info("RESPOND $it")
            when (it.name) {
                "AkquireData" -> actionAkquireData(it.parameters["camera_id"]!!.asText(), it.parameters["stream_seconds"]!!.asInt())
                "Add" -> actionAdd(it.parameters["x"]!!.asInt(), it.parameters["y"]!!.asInt())
                "GetInfo" -> actionGetInfo()
                "Fail" -> actionFail()
                "CreateAction" -> createAction(it.parameters["name"]!!.asText(), it.parameters["notify"]!!.asBoolean())
                "SpawnAgent" -> spawnAgent(it.parameters["name"]!!.asText())
                "Deregister" -> deregister(false)
                in extraActions.map { a -> a.name } -> "Called extra action ${it.name}"
                else -> null
            }
        }

    }

    private fun actionAkquireData(camera_id: String, stream_seconds: Int): String {
        log.info("in 'AkquireData' action, waiting...")
        println(camera_id)
        val ffmpegCommand = mutableListOf(
        "ffmpeg",
        "-i",
        "rtsp://admin:admin12345@130.149.98.39:554",
        "-t",
        "10",
        "test.mkv"
        )

        val processBuilder = ProcessBuilder(ffmpegCommand)

        val processThread = thread(start = false) {
            try {
                val process = processBuilder.start()
                val exitCode = process.waitFor()
                println("Exited with code $exitCode")
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        processThread.start()
        return "Action 'AkquireData' of $name called with camera_id=$camera_id and stream_seconds=$stream_seconds"
    }

    private fun actionAdd(x: Int, y: Int) = x + y

    private fun actionFail() {
        throw RuntimeException("Action Failed (as expected)")
    }

    private fun actionGetInfo() = mapOf(
        Pair("name", name),
        Pair("lastMessage", lastMessage),
        Pair("lastBroadcast", lastBroadcast),
        Pair(AgentContainerApi.ENV_CONTAINER_ID, System.getenv(AgentContainerApi.ENV_CONTAINER_ID)),
        Pair(AgentContainerApi.ENV_PLATFORM_URL, System.getenv(AgentContainerApi.ENV_PLATFORM_URL)),
        Pair(AgentContainerApi.ENV_TOKEN, System.getenv(AgentContainerApi.ENV_TOKEN))
    )

    private fun createAction(name: String, notify: Boolean) {
        extraActions.add(Action(name, mapOf(), "String"))
        register(notify)
    }

    private fun spawnAgent(name: String) {
        system.spawnAgent(DataAgent(name))
    }

}
