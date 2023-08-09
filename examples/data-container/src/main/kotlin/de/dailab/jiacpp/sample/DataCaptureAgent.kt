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




class DataCaptureAgent(name: String, private val camera_id: String, private val stream_seconds: Int): AbstractContainerizedAgent(name=name) {

    private var lastMessage: Any? = null
    private var lastBroadcast: Any? = null

    private var extraActions = mutableListOf<Action>()

    override fun getDescription() = AgentDescription(
        this.name,
        this.javaClass.name,
        listOf(
            Action("CaptureData", mapOf(), "String"),
            Action("GetInfo", mapOf(), "Map"),
            Action("Fail", mapOf(), "void"),
            // actions for testing modifying agents and actions at runtime
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
                "CaptureData" -> actionCaptureData()
                "GetInfo" -> actionGetInfo()
                "Fail" -> actionFail()
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

    private fun actionCaptureData(): String {
        log.info("in 'CaptureData' action, waiting...")

        val sanitized_camera_id = sanitizeFileName(camera_id)
        val ffmpegCommand = mutableListOf(
        "ffmpeg",
        "-i",
        "$camera_id",
        "-t",
        "$stream_seconds",
        "$sanitized_camera_id.mkv"
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
        return "Action 'CaptureData' of $name called with camera_id=$camera_id and stream_seconds=$stream_seconds"
    }

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

}
