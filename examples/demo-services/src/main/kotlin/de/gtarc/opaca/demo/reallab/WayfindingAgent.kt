package de.gtarc.opaca.demo.reallab

import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.model.Parameter

val ROOMS = mapOf(
    1 to "ExperienceHub",
    2 to "Konferenzraum",
    3 to "ManagementBuero",
    4 to "FocusSpace",
    5 to "DesignThinkingSpace",
    6 to "CoWorkingSpace",
    7 to "LieferroboterTeleoperator",
    8 to "Lieferroboterraum",
    9 to "WCDamen",
    10 to "WCHerren",
    11 to "WCUnisex",
    12 to "Kueche",
    13 to "Technikraum",
    100 to "vip"
)

/**
 * Dummy-version of the agent controlling the ZEKI LED-based wayfinding system.
 * Since this service would have an effect on the real world, the dummy-version does not really
 * do anything, except finding the room id for a given room name.
 */
class WayfindingAgent : AbstractContainerizedAgent(name="wayfinding-agent") {


    override fun preStart() {
        super.preStart()

        this.addAction("FindRoomById", mapOf("roomId" to Parameter("integer")), null) {
            val roomId = it["roomId"]!!.asInt()
            this.startWayfinding(roomId)
        }

        this.addAction("FindRoomByName", mapOf("roomName" to Parameter("string")), null) {
            val roomName = it["roomName"]!!.asText()
            val roomId = this.getRoomIdFromHint(roomName)
            this.startWayfinding(roomId)
        }
    }

    // only logs info - could not figure out what else to return/output
    private fun startWayfinding(roomId: Int) {
        log.info("Start wayfinding to room $roomId ${ROOMS[roomId]}...")
        log.info("Wayfinding completed...")
    }

    private fun getRoomIdFromHint(hint: String): Int {
        if (Regex("experience|xp|hub", RegexOption.IGNORE_CASE).matches(hint)) return 1
        if (Regex("conf", RegexOption.IGNORE_CASE).matches(hint)) return 2
        if (Regex("management", RegexOption.IGNORE_CASE).matches(hint)) return 3
        if (Regex("focus", RegexOption.IGNORE_CASE).matches(hint)) return 4
        if (Regex("design|think", RegexOption.IGNORE_CASE).matches(hint)) return 5
        if (Regex("work", RegexOption.IGNORE_CASE).matches(hint)) return 6
        if (Regex("tele|operator", RegexOption.IGNORE_CASE).matches(hint)) return 7
        if (Regex("liefer|deliver|robot", RegexOption.IGNORE_CASE).matches(hint)) return 8
        if (Regex("damen|women", RegexOption.IGNORE_CASE).matches(hint)) return 9
        if (Regex("herren|men", RegexOption.IGNORE_CASE).matches(hint)) return 10
        if (Regex("wc|toilet|unisex", RegexOption.IGNORE_CASE).matches(hint)) return 11
        if (Regex("kueche|küche|kitchen", RegexOption.IGNORE_CASE).matches(hint)) return 12
        if (Regex("technik|technic|server", RegexOption.IGNORE_CASE).matches(hint)) return 13
        if (Regex("vip", RegexOption.IGNORE_CASE).matches(hint)) return 100
        return -1
    }

}
