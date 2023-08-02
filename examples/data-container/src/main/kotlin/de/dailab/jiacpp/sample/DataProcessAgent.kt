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




class DataProcessAgent(name: String): AbstractContainerizedAgent(name=name) {

    private var lastMessage: Any? = null
    private var lastBroadcast: Any? = null

    private var extraActions = mutableListOf<Action>()

    override fun getDescription() = AgentDescription(
        this.name,
        this.javaClass.name,
        listOf(
            Action("ProcessData", mapOf(Pair("camera_id", "String"), Pair("compression_rate", "Int")), "String"),
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
                "ProcessData" -> actionProcessData(it.parameters["camera_id"]!!.asText(), it.parameters["compression_ratio"]!!.asInt())
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

    private fun sanitizeFileName(input: String): String {
        val ipAndPortPattern = "([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}:[0-9]{1,5})".toRegex()
        val matchResult = ipAndPortPattern.find(input)
        return matchResult?.value?.replace(Regex("[:.]"), "_") ?: ""
    }
    private fun actionProcessData(camera_id: String, compression_ratio: Int): String {
        log.info("in 'ProcessData' action, waiting...")

        val converted_ratio = 1.0/compression_ratio
        val sanitized_camera_id = sanitizeFileName(camera_id)

        val ffmpegCommand = mutableListOf(
            "ffmpeg",
            "-i",
            "$sanitized_camera_id.mkv",
            "-vf",
            "select='mod(n\\,${compression_ratio})',setpts=${converted_ratio}*PTS", // Keep every second frame and adjust speed
            "${sanitized_camera_id}_processed.mkv"
        )

        println(ffmpegCommand.joinToString(" "))

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
        return "Action 'ProcessData' of $name called with camera_id=$camera_id"
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
        system.spawnAgent(DataProcessAgent(name))
    }

}
