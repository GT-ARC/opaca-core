package de.dailab.jiacpp.sample

import de.dailab.jiacpp.api.AgentContainerApi
import de.dailab.jiacpp.container.AbstractContainerizedAgent
import de.dailab.jiacpp.container.Invoke
import de.dailab.jiacpp.model.Action
import de.dailab.jiacpp.model.AgentDescription
import de.dailab.jiacpp.model.Message
import de.dailab.jiacvi.behaviour.act


class SampleAgent(name: String): AbstractContainerizedAgent(name=name) {

    private var lastMessage: Any? = null
    private var lastBroadcast: Any? = null

    private var extraActions = mutableListOf<Action>()

    override fun getDescription() = AgentDescription(
        this.name,
        this.javaClass.name,
        listOf(
            Action("DoThis", mapOf(Pair("message", "String"), Pair("sleep_seconds", "Int")), "String"),
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
                "DoThis" -> actionDoThis(it.parameters["message"]!!.asText(), it.parameters["sleep_seconds"]!!.asInt())
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
        Pair(AgentContainerApi.ENV_CONTAINER_ID, System.getenv(AgentContainerApi.ENV_CONTAINER_ID)),
        Pair(AgentContainerApi.ENV_PLATFORM_URL, System.getenv(AgentContainerApi.ENV_PLATFORM_URL)),
        Pair(AgentContainerApi.ENV_TOKEN, System.getenv(AgentContainerApi.ENV_TOKEN))
    )

    private fun createAction(name: String, notify: Boolean) {
        extraActions.add(Action(name, mapOf(), "String"))
        register(notify)
    }

    private fun spawnAgent(name: String) {
        system.spawnAgent(SampleAgent(name), self)
    }

}
