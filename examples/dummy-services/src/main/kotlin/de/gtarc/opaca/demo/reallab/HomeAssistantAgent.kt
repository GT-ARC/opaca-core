package de.gtarc.opaca.demo.reallab

import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.model.Parameter
import kotlin.random.Random

/**
 * Dummy-version of the agent interacting with the ZEKI Home Assistant for reading various sensors.
 * In reality, there are many more sensors per room, one for each value to be measured (temperature,
 * co2, etc.), but this has been simplified here. The get-value action just returns a random number.
 */
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

    override fun setupAgent() {
        addAction("GetSensorsList", "Get list of all devices with 'multisensor' in their name. Those include the different sub-sensors of the multisensors, each including the main sensor's ID in their name as well as what they measure.", mapOf(), Parameter("array", true, Parameter.ArrayItems("string", null))) {
            actionGetMultisensors()
        }
        addAction("GetSensorId", "Get sensor ID corresponding to a given room. Room name does not have to be a perfect match.", mapOf(
            "room" to Parameter("string")
        ), Parameter("string", false)) { 
            getSensorFromRoomHint(it["room"]!!.asText())
        }
        addAction("GetTemperature", "Get temperature value for a given sensor", mapOf(
            "sensorId" to Parameter("string")
        ), Parameter("number")) { 
            actionGetValue(it["sensorId"]!!.asText(), "temperature")
        }
        addAction("GetCo2", "Get Co2 value for a given sensor", mapOf(
            "sensorId" to Parameter("string")
        ), Parameter("number")) { 
            actionGetValue(it["sensorId"]!!.asText(), "co2")
        }
        addAction("GetValue", "Get the value for a given sensor and property, searching in the list of sensors for a match", mapOf(
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
        return when (key.lowercase()) {
            "temperature" -> Random.nextDouble(20.0, 30.0)
            "co2" -> Random.nextDouble(400.0, 1500.0)
            "noise" -> Random.nextDouble(40.0, 80.0)
            "humidity" -> Random.nextDouble(20.0, 80.0)
            else -> Random.nextDouble(0.0, 100.0)
        }
    }

    private fun getSensorFromRoomHint(hint: String): String? {
        return SENSORS.entries.find { Regex(it.value, RegexOption.IGNORE_CASE).find(hint) != null }?.key
    }

}
