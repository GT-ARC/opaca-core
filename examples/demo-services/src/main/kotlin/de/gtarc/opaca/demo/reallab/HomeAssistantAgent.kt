package de.gtarc.opaca.demo.reallab

import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.model.Parameter
import kotlin.random.Random
import kotlin.text.Regex


class HomeAssistantAgent: AbstractContainerizedAgent(name="home-assistant-agent") {

    val SENSORS = mapOf(
        "104" to "experience|xp|hub",
        "115" to "conf|meeting",
        "110" to "management|mgmt",
        "109" to "focus",
        "114" to "recept|lobby|entr",
        "112" to "corrid|hall",
        "106" to "design|think",
        "103" to "work|west",
        "111" to "work|east",
        "107" to "robot|liefer|deliver",
        "108" to "admin|electr",
        "113" to "kueche|küche|kitchen"
    )

    override fun preStart() {
        super.preStart()

        addAction("GetSensorsList", mapOf(), Parameter("array", true, Parameter.ArrayItems("string", null))) {
            actionGetMultisensors()
        }
        addAction("GetSensorId", mapOf(
            "room" to Parameter("string")
        ), Parameter("string", false)) { 
            getSensorFromRoomHint(it["room"]!!.asText())
        }

        addAction("GetTemperature", mapOf(
            "sensorId" to Parameter("string")
        ), Parameter("number")) { 
            actionGetValue(it["sensorId"]!!.asText(), "temperature")
        }
        addAction("GetCo2", mapOf(
            "sensorId" to Parameter("string")
        ), Parameter("number")) { 
            actionGetValue(it["sensorId"]!!.asText(), "co2")
        }
        addAction("GetValue", mapOf(
            "sensorId" to Parameter("string"),
            "key" to Parameter("string")
        ), Parameter("number")) { 
            actionGetValue(it["sensorId"]!!.asText(), it["key"]!!.asText())
        }
    }

    private fun actionGetMultisensors(): List<String> {
        return SENSORS.keys.toList()
    }

    private fun actionGetValue(sensor: String, key: String): Double {
        log.info("Getting the $key value of the sensor $sensor...")
        return Random.nextDouble(-20.0,50.0)
    }

    private fun getSensorFromRoomHint(hint: String): String? {
        return SENSORS.entries.find { Regex(it.value, RegexOption.IGNORE_CASE).find(hint) != null }?.key
    }

}
