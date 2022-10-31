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
            Action("DoThis", mapOf(Pair("message", "String"), Pair("sleep_seconds", "Int")), "String")
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
                "DoThis" -> actionDoThis(it.parameters["message"]!!.asText(), it.parameters["sleep_seconds"]!!.asInt())
                else -> null
            }
        }

    }

    private fun actionDoThis(message: String, sleep_seconds: Int): String {
        log.info("in 'DoThis' action, waiting...")
        println(message)
        Thread.sleep(1000 * sleep_seconds.toLong())
        log.info("done waiting")
        return "Action 'DoThis' Called with message=$message and sleep_seconds=$sleep_seconds"
    }

}
