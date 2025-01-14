package de.gtarc.opaca.demo.reallab

import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.model.Parameter
import java.net.URI
import java.net.HttpURLConnection;

/**
 * Dummy version of agent controlling the shelves in the ZEKI kitchen. The agent can tell where
 * certain items can be found (hardcoded according to actual shelves in the kitchen) and open and
 * close those shelves (which, of course, does nothing in this version). Opening and closing actually
 * do exactly the same thing, as the real shelves don't know their state and can only be toggled.
 */
class ShelfAgent : AbstractContainerizedAgent(name="shelf-agent") {

    val CONTENTS = mapOf(
        1 to listOf("clean", "sponge", "towel"),
        2 to listOf("glass", "cup", "pot"),
        3 to listOf("plate", "bowl", "saucer"),
        4 to listOf("coffee", "tea", "beans")
    )

    val SHELF_NAME = mapOf(
        1 to "left shelf",
        2 to "middle left shelf",
        3 to "middle right shelf",
        4 to "right shelf"
    )

    override fun preStart() {
        super.preStart()

        this.addAction("FindInShelf", mapOf(
            "item" to Parameter("string")
        ), Parameter("integer")) {
            findInShelf(it["item"]!!.asText())
        }
        this.addAction("OpenShelf", mapOf(
            "shelf" to Parameter("integer")
        ), Parameter("boolean")) {
            openShelf(it["shelf"]!!.asInt())
        }
        this.addAction("CloseShelf", mapOf(
            "shelf" to Parameter("integer")
        ), Parameter("boolean")) {
            closeShelf(it["shelf"]!!.asInt())
        }
    }

    fun findInShelf(item: String): Int {
        for ((shelf, contents) in CONTENTS.entries) {
            if (contents.any {item.contains(it)}) {
                return shelf
            }
        }
        return -1
    }

    fun openShelf(shelf: Int): Boolean {
        return shelf in SHELF_NAME
    }

    fun closeShelf(shelf: Int): Boolean {
        return openShelf(shelf)
    }

}