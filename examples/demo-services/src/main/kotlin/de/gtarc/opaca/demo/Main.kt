package de.gtarc.opaca.demo

import de.dailab.jiacvi.communication.LocalBroker
import de.dailab.jiacvi.dsl.agentSystem
import de.gtarc.opaca.container.ContainerAgent
import de.gtarc.opaca.util.ConfigLoader

fun main() {
    val image = ConfigLoader.loadContainerImageFromResources("/demo-services.json")
    agentSystem("demo-agents") {
        enable(LocalBroker)
        agents {
            add(ContainerAgent(image))
            add(DeskBookingAgent())
            add(ServletAgent())
        }
    }.start()
}
