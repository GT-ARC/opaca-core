package de.dailab.jiacpp.sample

import de.dailab.jiacpp.api.AgentContainerApi
import de.dailab.jiacpp.container.AbstractContainerizedAgent
import de.dailab.jiacpp.container.Invoke
import de.dailab.jiacpp.container.Stream
import de.dailab.jiacpp.model.Action
import de.dailab.jiacpp.model.AgentDescription
import de.dailab.jiacpp.model.Message
import de.dailab.jiacvi.behaviour.act

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.OutputStreamWriter
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.File


class DataOrchestratorAgent(name: String, private val camera_id: String): AbstractContainerizedAgent(name=name) {

    private var lastMessage: Any? = null
    private var lastBroadcast: Any? = null

    private var extraActions = mutableListOf<Action>()

    override fun getDescription() = AgentDescription(
        this.name,
        this.javaClass.name,
        listOf(
            Action("CaptureResults", mapOf(Pair("containerId", "String")), "String"),
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
                "CaptureResults" -> actionCaptureResults(it.parameters["containerId"]!!.asText())
                "GetInfo" -> actionGetInfo()
                "Fail" -> actionFail()
                "Deregister" -> deregister(false)
                in extraActions.map { a -> a.name } -> "Called extra action ${it.name}"
                else -> null
            }
        }

    }
    // curl -o test.mkv http://localhost:8082/stream/CaptureResults/sample2?forward=false

    private fun sanitizeFileName(input: String): String {
        val ipAndPortPattern = "([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}:[0-9]{1,5})".toRegex()
        val matchResult = ipAndPortPattern.find(input)
        return matchResult?.value?.replace(Regex("[:.]"), "_") ?: ""
    }

    private fun actionCaptureResults(containerId: String): String {
        val sanitized_camera_id = sanitizeFileName(camera_id)
        val outputFilePath = "${sanitized_camera_id}_processed.mkv";
        val responseEntity: ResponseEntity<StreamingResponseBody> = sendOutboundStreamRequest("GetStream", null, containerId, false)

        val file = File(outputFilePath)
        FileOutputStream(file).use { outputStream ->
            responseEntity.body?.apply {
                outputStream.use { os -> 
                    this.writeTo(os)
                }
            }
        }

        return "Stream is transferred"
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
