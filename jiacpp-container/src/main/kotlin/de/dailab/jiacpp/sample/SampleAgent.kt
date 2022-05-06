package de.dailab.jiacpp.sample

import de.dailab.jiacpp.container.CONTAINER_AGENT
import de.dailab.jiacpp.container.ContainerAgent
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
            listOf<Action>()
        )

        val ref = system.resolve(CONTAINER_AGENT)
        ref invoke ask<Boolean>(desc) {
            println("REGISTERED: $it")
        }
    }

    override fun behaviour() = act {

        on<Message> {
            log.info("ON $it")
        }

        listen<Message>("topic") {
            log.info("LISTEN $it")
        }

        respond<ContainerAgent.Invoke, Any?> {
            log.info("RESPOND $it")
            when (it.name) {
                "DoThis" -> actionDoThis(it.parameters["foo"]!!.asText(), it.parameters["bar"]!!.asInt())
                else -> null
            }
        }

    }

    private fun actionDoThis(foo: String, bar: Int): String {
        println("in do this, waiting...")
        Thread.sleep(5000)
        println("done waiting")
        return "Action 'DoThis' Called with foo=$foo and bar=$bar"
    }

}
