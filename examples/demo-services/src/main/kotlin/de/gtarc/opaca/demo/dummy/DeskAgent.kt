package de.gtarc.opaca.demo.dummy

import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.model.Parameter
import de.gtarc.opaca.model.Parameter.ArrayItems
import kotlin.random.Random

/**
 * Dummy-agent providing actions for seeing bookings of imaginary desks. Somewhat inspired by
 * a similar system at ZEKI, but much dumbed down and only returning hard-coded values.
 */
class DeskBookingAgent(): AbstractContainerizedAgent(name="desk-booking-agent") {

    val rooms = listOf("Co-Working Space", "Robot Space", "Focus Space", "Experience Hub")

    val desks = mutableMapOf<String, MutableList<Int>>()

    override fun preStart() {
        super.preStart()

        addAction("GetRooms", mapOf(
        ), Parameter("array", true, ArrayItems("string", null))) {
            actionGetRooms()
        }
        addAction("GetDesks", mapOf(
            "room" to Parameter("string", true)
        ), Parameter("array", true, ArrayItems("integer", null))) {
            actionGetDesks(it["room"]!!.asText())
        }
        addAction("IsFree", mapOf(
            "room" to Parameter("string", true),
            "desk" to Parameter("integer", true)
        ), Parameter("boolean")) {
            actionIsFree(it ["room"]!!.asText(), it["desk"]!!.asInt())
        }
        addAction("BookDesk", mapOf(
            "room" to Parameter("string", true),
            "desk" to Parameter("integer", true)
        ), Parameter("boolean")) {
            actionBookDesk(it ["room"]!!.asText(), it["desk"]!!.asInt())
        }
    }

    fun actionGetRooms(): List<String> {
        println("Getting valid rooms...")
        return rooms
    }

    fun actionGetDesks(room: String): List<Int> {
        println("Getting desk ids for room $room ...")
        if (rooms.contains(room) && ! desks.containsKey(room)) {
            desks[room] = (0..30).filter { Random.nextBoolean() }.toMutableList()
        }
        return desks[room] ?: listOf()
    }

    fun actionIsFree(room: String, desk: Int): Boolean {
        println("Checking if desk $desk is free ...")
        return actionGetDesks(room).contains(desk)
    }

    fun actionBookDesk(room: String, desk: Int): Boolean {
        println("Booking desk $desk ...")
        if (actionIsFree(room, desk)) {
            desks[room]!!.remove(desk)
            return true
        }
        return false
    }

}
