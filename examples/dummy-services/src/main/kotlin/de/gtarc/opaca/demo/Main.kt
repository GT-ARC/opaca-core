package de.gtarc.opaca.demo

import de.dailab.jiacvi.communication.LocalBroker
import de.dailab.jiacvi.dsl.agentSystem
import de.gtarc.opaca.container.ContainerAgent
import de.gtarc.opaca.util.ConfigLoader
import de.gtarc.opaca.demo.reallab.*

fun main() {
    val image = ConfigLoader.loadContainerImageFromResources("/reallabor-dummy.json")
    agentSystem("reallab-dummy-agents") {
        enable(LocalBroker)
        agents {
            add(ContainerAgent(image))
            add(FridgeAgent())
            add(HomeAssistantAgent())
            add(ShelfAgent())
            add(WayfindingAgent())
            add(RoomBookingAgent())
        }
    }.start()
}
