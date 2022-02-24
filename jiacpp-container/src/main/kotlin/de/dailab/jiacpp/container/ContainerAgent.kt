package de.dailab.jiacpp.container

import de.dailab.jiacpp.api.AgentContainerApi
import de.dailab.jiacpp.model.AgentContainer
import de.dailab.jiacpp.model.AgentContainerImage
import de.dailab.jiacpp.model.AgentDescription
import de.dailab.jiacpp.model.Message
import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.behaviour.act
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletHandler
import org.eclipse.jetty.servlet.ServletHolder

/**
 * Agent providing the REST interface of the Agent Container using a simple Jetty server.
 * The API is not quite as fancy one provided using Spring Boot or similar, but might be
 * sufficient, since all "outside" calls would go through the Runtime Container.
 * Still, might be improved a bit...
 */
class ContainerAgent(val image: AgentContainerImage): Agent() {

    private val server = Server(8082)

    private val servlet = object : HttpServlet() {

        override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
            log.info("received GET $request")

            // TODO use different regular expressions to match the paths here?

	        // TODO "/info" -> AgetnCotantainer
            // TODO "/agents" -> List<AgentDescription>
	        // TODO "/agents/{agentId}" -> AgentDescription

        }

        override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
            log.info("received POST $request")

            // TODO "/send/{agentId}" Message -> void
            // TODO "/broadcast/{channel}" Message -> void
			// TODO "/invoke/{action}" Map<String, Object> -> Object
        	// TODO "/invoke/{action}/{agentId}" Map<String, Object> -> Object

            /*
            val json: String = request.reader.lines().collect(Collectors.joining())
            when (request.pathInfo) {
                "/event" -> {
                    val event = RestHelper.readJson(json, MesEvent::class.java)
                    val ref = system.resolve("mediator")
                    ref.tell(event)
                }
                else -> {
                    log.warn("POST on unexpected path: ${request.pathInfo}")
                }
            }
            */
        }
    }

    private val impl = object : AgentContainerApi {

        override fun getInfo(): AgentContainer {
            TODO("Not yet implemented")
        }

        override fun getAgents(): MutableList<AgentDescription> {
            TODO("Not yet implemented")
        }

        override fun getAgent(agentId: String?): AgentDescription {
            TODO("Not yet implemented")
        }

        override fun send(agentId: String?, message: Message?) {
            TODO("Not yet implemented")
        }

        override fun broadcast(channel: String?, message: Message?) {
            TODO("Not yet implemented")
        }

        override fun invoke(action: String?, parameters: MutableMap<String, Any>?): Any {
            TODO("Not yet implemented")
        }

        override fun invoke(agentId: String?, action: String?, parameters: MutableMap<String, Any>?): Any {
            TODO("Not yet implemented")
        }
    }

    // start Web Server
    override fun preStart() {
        super.preStart()
        val handler = ServletHandler()
        handler.addServletWithMapping(ServletHolder(servlet), "/*")
        server.handler = handler
        server.start()
    }

    // stop Web Server
    override fun postStop() {
        server.stop()
        super.postStop()
    }

    override fun behaviour() = act {

        // Container Agent does not have any behaviour of its own?
        // should probably handle messages to be sent to other containers/platforms

    }

}
