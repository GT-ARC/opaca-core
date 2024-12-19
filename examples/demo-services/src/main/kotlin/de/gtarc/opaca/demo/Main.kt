package de.gtarc.opaca.demo

import de.dailab.jiacvi.communication.LocalBroker
import de.dailab.jiacvi.dsl.agentSystem
import de.gtarc.opaca.container.ContainerAgent
import de.gtarc.opaca.util.ConfigLoader
import de.gtarc.opaca.demo.reallab.*
import de.gtarc.opaca.demo.dummy.*

fun main() {
    val image = ConfigLoader.loadContainerImageFromResources("/demo-image.json")
    agentSystem("demo-agents") {
        enable(LocalBroker)
        agents {
            add(ContainerAgent(image))
            // dummy-fied Real-lab agents
            /*
            add(RoomBookingAgent())
            add(SensorsAgent())
            add(FridgeAgent())
            add(WayfindingAgent())
            add(ShelfAgent())
            add(HomeAssistantAgent())
             */
            // additional dummy agents
            /*
            add(RoomAgent())
            add(DeskAgent())
            add(CarFleetAgent())
             */
            add(ServletAgent())
        }
    }.start()
}
