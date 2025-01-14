package de.gtarc.opaca.demo.reallab

import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.model.Parameter

/**
 * Dummy-version of the agent for managing the inventory list of the ZEKI fridge.
 * Can be used for listing, adding and removing grocery items from the inventory.
 */
class FridgeAgent : AbstractContainerizedAgent(name="fridge-agent") {

    data class Grocery(
        val name: String,
        val amount: Int,
        val expirationDate: String,
        val category: String
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

    override fun preStart() {
        super.preStart()

        this.addAction("GetGroceries", mapOf(
            "category" to Parameter("string")
        ), Parameter("array", true, Parameter.ArrayItems("object", null))) {
            getGroceries(it["category"]!!.asText())
        }

        this.addAction("AddGroceries", mapOf(
            "name" to Parameter("string"),
            "amount" to Parameter("integer"),
            "expirationDate" to Parameter("string"),
            "category" to Parameter("string")
        ), Parameter("string")) {
            addGroceries(
                it["name"]!!.asText(),
                it["amount"]!!.asInt(),
                it["expirationDate"]!!.asText(),
                it["category"]!!.asText()
            )
        }

        this.addAction("RemoveGroceries", mapOf(
            "names" to Parameter("array", true, Parameter.ArrayItems("string", null))
        ), Parameter("string")) {
            removeGroceries(it["names"]!!.asText())
        }
    }

    private fun getGroceries(category: String): List<Grocery> {
        log.info("Getting groceries of $category category...")
        return GROCERIES.filter { it.category == category }
    }

    private fun addGroceries(name: String, amount: Int, expirationDate: String, category: String) {
        log.info("Adding new grocery $name...")
        GROCERIES.add(Grocery(name, amount, expirationDate, category))
    }

    private fun removeGroceries(namesParam: String) {
        // ??
    }

}