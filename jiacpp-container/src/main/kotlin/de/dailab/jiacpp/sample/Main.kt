package de.dailab.jiacpp.sample

import de.dailab.jiacpp.container.ContainerAgent
import de.dailab.jiacpp.model.AgentContainerImage
import de.dailab.jiacvi.communication.LocalBroker
import de.dailab.jiacvi.dsl.agentSystem

fun main() {

    // could also be provided as a JSON file in the resources dir
    val image = AgentContainerImage(
            "sample-docker-image-name",
            listOf(),
            listOf(),
            "Sample JIAC++ Agent Container",
            "Just a sample container doing nothing but receiving and reacting to API calls",
            "DAI-Lab"
    )

    agentSystem("jiacpp-sample-container") {
        enable(LocalBroker)
        agents {
            add(ContainerAgent(image))
            add(SampleAgent())
        }
    }.start()
}
