package de.gtarc.opaca.demo.reallab

import com.fasterxml.jackson.annotation.JsonProperty
import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.model.Parameter
import de.gtarc.opaca.util.RestHelper

/**
 * Dummy-version of the agent for managing the inventory list of the ZEKI fridge.
 * Can be used for listing, adding and removing grocery items from the inventory.
 */
class FridgeAgent : AbstractContainerizedAgent(name="fridge-agent") {

    data class Grocery(
        @JsonProperty("name")
        var name: String,
        @JsonProperty("amount")
        var amount: Int,
        @JsonProperty("expirationDate")
        var expirationDate: String,
        @JsonProperty("category")
        var category: String
    )

    // hardcoded values for groceries (adding/removing is ok)
    val GROCERIES = mutableListOf<Grocery>(
        Grocery("cucumber", 3, "12.06.2024", "vegetables"),
        Grocery("tomato",7, "10.07.2024", "vegetables"),
        Grocery("avocado", 2, "11.06.2024", "vegetables"),
        Grocery("banana", 6, "11.06.2024", "fruits"),
        Grocery("apple", 9,  "23.07.2024", "fruits"),
        Grocery("strawberries", 21, "10.07.2024","fruits"),
        Grocery("blueberries", 33, "12.06.2024", "fruits"),
        Grocery("milk", 1, "10.07.2024", "dairy"),
        Grocery("chicken", 2, "12.06.2024", "meat"),
        Grocery("beef", 3, "11.08.2024", "meat"),
        Grocery("water", 3, "12.09.2027", "drinks"),
        Grocery("coke", 1, "30.09.2027", "drinks")
    )


    override fun setupAgent() {
        this.addAction("GetGroceries", mapOf(
            "category" to Parameter("string", false, null)
        ), Parameter("array", true, Parameter.ArrayItems("Grocery", null))) {
            getGroceries(it["category"]?.asText())
        }

        this.addAction("AddGroceries", mapOf(
            "item" to Parameter("Grocery")
        ), null) {
            val item = RestHelper.mapper.treeToValue<Grocery>(it["item"]!!, Grocery::class.java)
            addGroceries(item)
        }

        this.addAction("RemoveGrocery", mapOf(
            "name" to Parameter("string")
        ), Parameter("boolean")) {
            removeGrocery(it["name"]!!.asText())
        }
    }

    private fun getGroceries(category: String?): List<Grocery> {
        log.info("Getting groceries of $category category...")
        return GROCERIES.filter { category == null || it.category == category }
    }

    private fun addGroceries(item: Grocery) {
        log.info("Adding new grocery $item...")
        GROCERIES.add(item)
    }

    private fun removeGrocery(name: String): Boolean {
        val g = GROCERIES.find { it.name == name }
        return if (g != null) {
            if (g.amount > 1) {
                g.amount -= 1
            } else {
                GROCERIES.remove(g)
            }
            true;
        } else {
            false;
        }
    }

}