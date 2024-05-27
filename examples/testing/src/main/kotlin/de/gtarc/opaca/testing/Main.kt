package de.gtarc.opaca.testing

import de.gtarc.opaca.testing.manyonone.runManyOnOneTest


fun main() {
    testEnv()
    runManyOnOneTest()
}

private fun testEnv() {
    if (System.getenv("PLATFORM_URL").isNullOrEmpty() || System.getenv("CONTAINER_ID").isNullOrEmpty()) {
        println("""
            If you are running this from the command line, make sure that both the
            `PLATFORM_URL` and `CONTAINER_ID` environment variables are set. Usually,
            those are set by the Runtime Platform when starting the container. For the
            sake of easily running this test, the PLATFORM_URL should point back to the
            AgentContainer itself; the value of CONTAINER_ID does not matter, i.e.

            export CONTAINER_ID=12345
            export PLATFORM_URL="http://localhost:8082"
        """.trimIndent())
        System.exit(1)
    }
}

