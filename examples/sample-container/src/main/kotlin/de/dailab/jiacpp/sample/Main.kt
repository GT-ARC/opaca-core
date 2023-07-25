package de.dailab.jiacpp.sample

import de.dailab.jiacpp.container.ContainerAgent
import de.dailab.jiacpp.util.ConfigLoader
import de.dailab.jiacvi.communication.LocalBroker
import de.dailab.jiacvi.dsl.agentSystem

fun main() {
    val image = ConfigLoader.loadContainerImageFromResources("/container.json")
    agentSystem("jiacpp-sample-container") {
        enable(LocalBroker)
        agents {
            add(ContainerAgent(image))
            add(SampleAgent("sample1"))
            add(SampleAgent("sample2"))
            add(SimpleUIAgent())
            add(SimpleUDPAgent())
        }
    }.start()
}
