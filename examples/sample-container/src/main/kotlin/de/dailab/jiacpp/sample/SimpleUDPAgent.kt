package de.dailab.jiacpp.sample

import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.behaviour.act

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.charset.StandardCharsets

class SimpleUDPAgent: Agent(overrideName="simple-udp-agent") {

    private val socket = DatagramSocket(8889)
    private var running = false

    override fun preStart() {
        running = true
        val buffer = ByteArray(1024)

        println("UDP server is running.")

        Thread {
            while (running) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)

                val message = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                println("Received: $message")

                // Sending a response
                val responseData = "It Works!".toByteArray(StandardCharsets.UTF_8)
                val responsePacket = DatagramPacket(responseData, responseData.size, packet.address, packet.port)
                socket.send(responsePacket)
            }
        }.start()
    }

    override fun postStop() {
        running = false
        socket.close()
        println("UDP server is stopped.")
    }

    override fun behaviour() = act {}

}
