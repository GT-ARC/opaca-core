package de.gtarc.opaca.sample

import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.model.Parameter


class BachelorAgent(name: String): AbstractContainerizedAgent(name=name) {

    override fun preStart() {
        addAction("Add", mapOf(
                "x" to Parameter("x", "Int", false),
                "y" to Parameter("y", "Int", false)
            ), "Int") {
            actionAdd(it["x"]!!.asInt(), it["y"]!!.asInt())
        }
        addAction("TestAction", mapOf(
            "Names" to Parameter("names", "Array", false, "String"),
            "Shared car" to Parameter("Shared car", "car", false)
        ), "void") {
            println("Called TestAction: $it")
        }

        super.preStart()
    }

    private fun actionAdd(x: Int, y: Int) = x + y

}
