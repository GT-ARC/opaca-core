package de.gtarc.opaca.sample

import de.dailab.jiacvi.communication.LocalBroker
import de.dailab.jiacvi.dsl.agentSystem
import de.gtarc.opaca.container.ContainerAgent
import de.gtarc.opaca.util.ConfigLoader

fun main() {
    val image = ConfigLoader.loadContainerImageFromResources("/sample-image.json")
    agentSystem("opaca-sample-container") {
        enable(LocalBroker)
        agents {
            add(ContainerAgent(image, true))
            add(SampleAgent("sample1"))
            add(SampleAgent("sample2"))
            add(SimpleUIAgent())
            add(SimpleUDPAgent())
        }
    }.start()
}
