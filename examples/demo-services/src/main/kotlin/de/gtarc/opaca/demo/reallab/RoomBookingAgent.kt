package de.gtarc.opaca.demo.reallab

import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.model.Parameter
import de.gtarc.opaca.model.Parameter.ArrayItems


data class Booking(val roomName: String, val bookingDate: String)

class RoomBookingAgent : AbstractContainerizedAgent(name="room-booking-agent") {

    override fun preStart() {
        super.preStart()

        this.addAction("GetUserBookings", mapOf(), Parameter("array", true, ArrayItems("object", null))) {
            this.getUserBookings()
        }

        this.addAction("GetLocationBookings", mapOf(
            "location" to Parameter("string")
        ), Parameter("array", true, ArrayItems("object", null))) {
            this.getLocationBookings(it["location"]!!.asText())
        }
    }

    private fun getUserBookings(): List<String> {
        log.info("Getting the bookings of this user...")
        return listOf(Booking("room1", "12.06.2024"),
                      Booking("room2", "11.06.2024"))
                      .map { it.roomName }
    }

    private fun getLocationBookings(location: String): List<String> {
        log.info("Getting the bookings in the location $location...")
        return listOf(Booking("room1", "12.06.2024"),
                      Booking("room2", "11.06.2024"),
                      Booking("room3", "13.06.2024"))
                      .map { it.roomName }
    }

}
