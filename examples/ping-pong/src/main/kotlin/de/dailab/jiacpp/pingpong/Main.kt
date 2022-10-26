package de.dailab.jiacpp.pingpong

import de.dailab.jiacpp.container.ContainerAgent
import de.dailab.jiacpp.util.ConfigLoader
import de.dailab.jiacvi.communication.LocalBroker
import de.dailab.jiacvi.dsl.agentSystem
import java.lang.IllegalArgumentException

fun main(args: Array<String>) {

    if (args.isEmpty())
        throw IllegalArgumentException("Please provide 'ping' or 'pong' as command line parameter!")

    val image_config = when (args[0]) {
        "ping" -> "/ping-container.json"
        "pong" -> "/pong-container.json"
        else -> throw IllegalArgumentException("Argument must be 'ping' or 'pong'!")
    }

    val image = ConfigLoader.loadContainerImageFromResources(image_config)
    agentSystem(image.imageName) {
        enable(LocalBroker)
        agents {
            add(ContainerAgent(image))
            add(if ("ping" == args[0]) PingAgent() else PongAgent())
        }
    }.start()
}
