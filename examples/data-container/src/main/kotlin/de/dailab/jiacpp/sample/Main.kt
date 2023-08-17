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
            add(DataCaptureAgent("Data-Capture-Agent", "rtsp://admin:admin12345@130.149.98.39:554", 10))
            add(DataProcessAgent("Data-Process-Agent", "rtsp://admin:admin12345@130.149.98.39:554", 2))
            add(DataStreamAgent("Data-Stream-Agent", "rtsp://admin:admin12345@130.149.98.39:554"))
        }
    }.start()
}
