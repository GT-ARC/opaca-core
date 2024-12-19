package de.gtarc.opaca.demo.dummy

import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.model.Parameter
import de.gtarc.opaca.model.Parameter.ArrayItems

class DeskAgent(): AbstractContainerizedAgent(name="desk-agent") {

    override fun preStart() {
        super.preStart()

        addAction("GetDesks", mapOf(
            "room" to Parameter("string", true)
        ), Parameter("array", true, ArrayItems("integer", null))) {
            actionGetDesks(it["room"]!!.asText())
        }
        addAction("IsFree", mapOf(
            "desk" to Parameter("integer", true)
        ), Parameter("boolean")) {
            actionIsFree(it["desk"]!!.asInt())
        }
        addAction("BookDesk", mapOf(
            "desk" to Parameter("integer", true)
        ), Parameter("array", true, ArrayItems("integer", null))) {
            actionBookDesk(it["desk"]!!.asInt())
        }
    }

    fun actionGetDesks(room: String): List<Int> {
        println("Getting desk ids for room $room ...")
        return (0..10).toList()
    }

    fun actionIsFree(desk: Int): Boolean {
        println("Checking if desk $desk is free ...")
        return (desk % 2 == 0)
    }

    fun actionBookDesk(desk: Int): Boolean {
        println("Booking desk $desk ...")
        return (desk % 2 == 0)
    }

}
