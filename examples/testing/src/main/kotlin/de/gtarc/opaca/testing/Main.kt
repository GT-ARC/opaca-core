package de.gtarc.opaca.testing

import de.gtarc.opaca.testing.manyonone.runManyOnOneTest
import kotlin.system.exitProcess


val TEST_CASES = mapOf(
    "many-on-one" to ::runManyOnOneTest
)

fun main(args: Array<String>) {
    if (System.getenv("PLATFORM_URL").isNullOrEmpty() || System.getenv("CONTAINER_ID").isNullOrEmpty()) {
        println("""
            When running this from the command line, make sure that both the
            `PLATFORM_URL` and `CONTAINER_ID` environment variables are set. Usually,
            those are set by the Runtime Platform when starting the container. For the
            sake of easily running this test, the PLATFORM_URL should point back to the
            AgentContainer itself; the value of CONTAINER_ID does not matter, i.e.

            export CONTAINER_ID=12345
            export PLATFORM_URL="http://localhost:8082"
        """.trimIndent())
        exitProcess(1)
    }

    val testCase = args.getOrNull(0)
    if (testCase == null) {
        println("Please specify the test case to run as a command line parameter.")
        exitProcess(1)
    }

    val callback = TEST_CASES[testCase]
    if (callback == null) {
        println("Invalid test case. Valid test cases are: ${TEST_CASES.keys}")
        exitProcess(1)
    } else {
        callback.invoke()
    }

}
