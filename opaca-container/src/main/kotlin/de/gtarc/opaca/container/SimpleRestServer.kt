package de.gtarc.opaca.container

import de.gtarc.opaca.model.ErrorResponse
import de.gtarc.opaca.api.AgentContainerApi
import de.gtarc.opaca.model.Message
import de.gtarc.opaca.util.RestHelper
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.AbstractConnector
import org.eclipse.jetty.servlet.ServletHandler
import org.eclipse.jetty.servlet.ServletHolder
import java.util.stream.Collectors
import java.util.concurrent.TimeUnit
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Minimal Jetty server for providing the REST interface. There's probably a better way to do this.
 * The goal is just a minimal REST server for providing the AgentContainer API to be called by the
 * Runtime Platform; things like a web interface, model checking, security, etc. should be provided
 * by the Runtime Platform and are thus intentionally not considered here.
 *
 * Previously, this was a part of the ContainerAgent itself. Moved to a separate class to unclutter
 * the ContainerAgent and "make room" for some basic exception handling, translating no-such-element
 * or actual internal errors (e.g. when executing an action) to appropriate HTTP status codes.
 */
class OpacaServer(val impl: AgentContainerApi, val port: Int, val token: String?) {

    private val server = Server(port)

    /**
     * servlet handling the different REST routes, delegating to `impl` for actual logic
     */
    private val servlet = object: HttpServlet() {

        /**
         * maps exception classes to error status codes
         */
        private val errorStatusCodes = mutableMapOf<Class<out Exception>, Int>()

        init {
            registerErrorCode(NoSuchElementException::class.java, 404)
            registerErrorCode(NotAuthenticatedException::class.java, 403)
        }

        override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
            try {
                checkToken(request)
                val path = request.requestURI
                val res = handleGet(path)
                writeResponse(response, HttpServletResponse.SC_OK, res)
            } catch (e: Exception) {
                handleError(response, e)
            }
        }

        override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
            try {
                checkToken(request)
                val path = request.requestURI  // NOTE: queryParams (?...) go to request.queryString
                val query = request.queryString
                val res = handlePost(path, query, request)
                writeResponse(response, 200, res)
            } catch (e: Exception) {
                handleError(response, e)
            }
        }

        private fun checkToken(request: HttpServletRequest) {
            val tokenFromRequest = request.getHeader("Authorization")?.removePrefix("Bearer ")
            if (! token.isNullOrEmpty() && tokenFromRequest != token) {
                throw NotAuthenticatedException("Unauthorized: Token does not match")
            }
        }

        private fun writeResponse(response: HttpServletResponse, code: Int, result: Any?) {
            if (result !is InputStream) {
                // regular JSON result
                response.contentType = "application/json"
                response.status = code
                response.writer.write(RestHelper.writeJson(result))
            } else {
                // streaming result
                response.contentType = "application/octet-stream"
                response.status = code
                result.transferTo(response.outputStream)
                response.flushBuffer()
            }
        }

        private fun handleError(response: HttpServletResponse, e: Exception) {
            val code = getErrorCode(e)
            val err = ErrorResponse(code, e.message, null)
            writeResponse(response, code, err)
        }

        fun getErrorCode(e: Exception): Int {
            return when (e) {
                is OpacaException -> e.statusCode
                else -> errorStatusCodes[e::class.java] ?: 500
            }
        }

        fun registerErrorCode(exceptionClass: Class<out Exception>, code: Int) {
            errorStatusCodes[exceptionClass] = code
        }

        private fun handleGet(path: String): Any? {

            val info = Regex("^/info$").find(path)
            if (info != null) {
                return impl.containerInfo
            }

            val agents = Regex("^/agents$").find(path)
            if (agents != null) {
                return impl.agents
            }

            val agentWithId = Regex("^/agents/([^/]+)$").find(path)
            if (agentWithId != null) {
                val id = agentWithId.groupValues[1]
                return impl.getAgent(id)
            }

            val getStreamOf = Regex("^/stream/([^/]+)(?:/([^/]+))?$").find(path)
            if (getStreamOf != null) {
                val stream = getStreamOf.groupValues[1]
                val agentId = getStreamOf.groupValues[2].ifEmpty { null }
                return impl.getStream(stream, agentId, "", false)
            }

            throw NoSuchElementException("Unknown path: $path")
        }

        private fun handlePost(path: String, query: String?, request: HttpServletRequest): Any? {
            val queryParams = parseQueryString(query)
            val timeout = queryParams.getOrDefault("timeout", "-1").toInt()

            val notify = Regex("^/notify/([^/]+)$").find(path)
            if (notify != null) {
                val actionName = notify.groupValues[1]
                impl.notifyAgentAboutAction(actionName)
            }

            val send = Regex("^/send/([^/]+)$").find(path)
            if (send != null) {
                val id = send.groupValues[1]
                val message = RestHelper.readObject(body(request), Message::class.java)
                return impl.send(id, message, "", false)
            }

            val broadcast = Regex("^/broadcast/([^/]+)$").find(path)
            if (broadcast != null) {
                val channel = broadcast.groupValues[1]
                val message = RestHelper.readObject(body(request), Message::class.java)
                return impl.broadcast(channel, message, "", false)
            }

            val invokeActOf = Regex("^/invoke/([^/]+)(?:/([^/]+))?$").find(path)
            if (invokeActOf != null) {
                val action = invokeActOf.groupValues[1]
                val agentId = invokeActOf.groupValues[2].ifEmpty { null }
                val parameters = RestHelper.readMap(body(request))
                return impl.invoke(action, parameters, agentId, timeout, "", false)
            }

            val postStreamTo = Regex("^/stream/([^/]+)(?:/([^/]+))?$").find(path)
            if (postStreamTo != null) {
                val stream = postStreamTo.groupValues[1]
                val agentId = postStreamTo.groupValues[2].ifEmpty { null }

                val byteArrayOutputStream = ByteArrayOutputStream()
                request.inputStream.use { input ->
                    byteArrayOutputStream.use { output ->
                        input.copyTo(output)
                    }
                }

                val byteArray = byteArrayOutputStream.toByteArray()
                return impl.postStream(stream, byteArray, agentId, "", false)
            }

            throw NoSuchElementException("Unknown path: $path")
        }

        // adapted from https://stackoverflow.com/a/17472462/1639625
        fun parseQueryString(queryString: String?) = (queryString ?: "")
            .split("&")
            .map { it.split("=") }
            .associate { Pair(it[0], if (it.size > 1) it[1] else "") }


        fun body(request: HttpServletRequest) = request.reader.lines().collect(Collectors.joining())

    }

    fun start() {
        val handler = ServletHandler()
        handler.addServletWithMapping(ServletHolder(servlet), "/*")
        server.handler = handler

        server.connectors.forEach { connector ->
            if (connector is AbstractConnector) {
                connector.idleTimeout = TimeUnit.MINUTES.toMillis(15) 
            }
        }

        server.start()
    }

    fun stop() {
        server.stop()
    }

    fun registerErrorCode(exceptionClass: Class<out Exception>, code: Int) {
        servlet.registerErrorCode(exceptionClass, code)
    }

    class NotAuthenticatedException(message: String): RuntimeException(message)
    class OpacaException(val statusCode: Int, message: String): Exception(message)
}

