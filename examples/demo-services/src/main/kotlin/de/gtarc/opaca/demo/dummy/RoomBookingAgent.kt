package de.gtarc.opaca.demo.dummy

import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.model.Parameter
import de.gtarc.opaca.model.Parameter.ArrayItems


data class Booking(val roomName: String, val bookingDate: String)

/**
 * Dummy-agent providing actions for seeing bookings of imaginary rooms. Somewhat inspired by
 * a similar system at ZEKI, but much dumbed down and only returning hard-coded values.
 */
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

    private fun getUserBookings(): List<Booking> {
        log.info("Getting the bookings of this user...")
        return listOf(Booking("room1", "12.06.2024"),
                      Booking("room2", "11.06.2024"))
    }

    private fun getLocationBookings(location: String): List<Booking> {
        log.info("Getting the bookings in the location $location...")
        return listOf(Booking(location, "12.06.2024"),
                      Booking(location, "11.06.2024"),
                      Booking(location, "13.06.2024"))
    }

}
