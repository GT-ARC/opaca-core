package de.dailab.jiacpp.sample

import de.dailab.jiacpp.container.AbstractContainerizedAgent
import de.dailab.jiacpp.container.Invoke
import de.dailab.jiacpp.model.Action
import de.dailab.jiacpp.model.AgentDescription
import de.dailab.jiacpp.model.Message
import de.dailab.jiacvi.behaviour.act


class SampleAgent: AbstractContainerizedAgent(name="sample") {

    override fun getDescription() = AgentDescription(
        this.name,
        this.javaClass.name,
        listOf(
            Action("DoThis", mapOf(Pair("foo", "String"), Pair("bar", "Int")), "String")
        )
    )

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
