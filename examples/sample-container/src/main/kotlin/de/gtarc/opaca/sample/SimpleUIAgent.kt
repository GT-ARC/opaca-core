package de.gtarc.opaca.sample

import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.behaviour.act
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletHandler
import org.eclipse.jetty.servlet.ServletHolder


/**
 * Agent providing a super-simple Web UI for testing exposed ports
 */
class SimpleUIAgent: Agent(overrideName="simple-ui-agent") {

    private val server = Server(8888)

    private val servlet = object : HttpServlet() {

        override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
            response.writer.write("It Works!")
        }

    }

    override fun preStart() {
        super.preStart()
        val handler = ServletHandler()
        handler.addServletWithMapping(ServletHolder(servlet), "/*")
        server.handler = handler
        server.start()
    }

    override fun postStop() {
        server.stop()
        super.postStop()
    }

    override fun behaviour() = act {}

}
