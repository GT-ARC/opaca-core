package de.dailab.jiacpp.sample

import de.dailab.jiacpp.container.CONTAINER_AGENT
import de.dailab.jiacpp.container.Invoke
import de.dailab.jiacpp.model.Action
import de.dailab.jiacpp.model.AgentDescription
import de.dailab.jiacpp.model.Message
import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.behaviour.act


class SampleAgent: Agent(overrideName="sample") {

    override fun preStart() {
        super.preStart()

        val desc = AgentDescription(
            this.name,
            this.javaClass.name,
            listOf(
                Action("DoThis", mapOf(Pair("foo", "String"), Pair("bar", "Int")), "String")
            )
        )

        val ref = system.resolve(CONTAINER_AGENT)
        ref invoke ask<Boolean>(desc) {
            log.info("REGISTERED: $it")
        }
    }

    override fun behaviour() = act {

        on<Message> {
            log.info("ON $it")
        }

        listen<Message>("topic") {
            log.info("LISTEN $it")
        }

        respond<Invoke, Any?> {
            log.info("RESPOND $it")
            when (it.name) {
                "DoThis" -> actionDoThis(it.parameters["foo"]!!.asText(), it.parameters["bar"]!!.asInt())
                else -> null
            }
        }

    }

    private fun actionDoThis(foo: String, bar: Int): String {
        log.info("in 'DoThis' action, waiting...")
        Thread.sleep(5000)
        log.info("done waiting")
        return "Action 'DoThis' Called with foo=$foo and bar=$bar"
    }

}
