package de.gtarc.opaca.demo.dummy

import de.dailab.jiacvi.behaviour.act
import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.model.Parameter
import io.javalin.Javalin
import java.time.Duration

/**
 * Agent showing a simple HTTP Servlet showing a notification and some arbitrary values
 * in a very generic fashion. The servlet auto-refreshs every second. Can be used as a
 * stand-in for a proper notifications-service or for showing the state of imaginary windows,
 * lights, etc.
 */
class ServletAgent : AbstractContainerizedAgent(name="servlet-agent") {

    // the current step (incremented each second)
    var step = 0

    // step when to clear the current message
    var stepClear = 0

    // message currently to be shown
    var message = ""

    // optional title to display
    var title = ""

    // map of arbitrary other values, e.g. state for some imaginary lamps or windows...
    val valuesMap = mutableMapOf<String, String>()
    val table = ""

    // simple web server running on port 8888 (see extra-ports in container.json)
    private val server = Javalin.create()
            .get("/") {
                val valueRows = valuesMap.entries.joinToString("\n") {
                    "<tr><td class=\"key\">${it.key}</td><td class=\"value\">${it.value}</td></tr>"
                }
                val html = """
                <html>
                    <head>
                        <meta http-equiv="refresh" content="1">
                        <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
                        <style>
                            body{
                                display:flex;
                                align-items:center;
                                justify-content:center;
                                font-family: sans-serif;
                            }
                            .container{
                                display:flex;
                                flex-direction: column;
                                height: 100%;
                                width: 100%;
                                position:fixed;
                                top:0;
                                left:0;
                            }
                            p{
                                text-align:center;
                                margin: 0;
                                padding: 0;
                            }
                            #title{
                                padding: 40px 0;
                                width:100%;
                                height:5%;
                                place-items:center;
                                justify-content:center;
                                font-size:50px;
                                font-weight: bold
                            }
                            #message-box{
                                padding: 30px 0;
                                width:100%;
                                height:10%;
                                text-align:center;
                                color:#B22222;
                                font-size:40px
                            }
                            #value-box{
                                margin: 100px 500px;
                                height:100%;
                                text-align:center;
                                place-items:center;
                                justify-content:center;
                            }
                            table {
                              height:100%;
                              border-collapse: collapse;
                              width: 100%;
                              font-size: 35px;
                            }
                            table .key {
                                text-align: left;
                                font-weight: bold
                            }
                            table .value {
                                text-align: center
                            }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div id="title">
                                <p>${title}</p>
                            </div>
                            <div id="message-box">
                                <p>${message}</p>
                            </div>
                            <div id="value-box">
                               <table>${valueRows}</table>
                            </div>                        
                        </div>
                    </body>
                </html>
"""
                it.contentType("text/html")
                it.result(html)
            }
            .get("/reset") {
                step = 0
                stepClear = 0
                message = ""
                title = ""
                valuesMap.clear()
            }

    override fun setupAgent() {
        addAction("ShowMessage", mapOf(
            "message" to Parameter("string"),
            "seconds" to Parameter("integer", false)
        ), null) {
            actionShowMessage(it["message"]!!.asText(), (it["seconds"]?.asInt() ?: -1))
        }
        addAction("GetValue", mapOf(
            "key" to Parameter("string")
        ), Parameter("string")) { 
            actionGetValue(it["key"]!!.asText())
        }
        addAction("SetTitle", mapOf(
            "title" to Parameter("string")
        ), null) {
            actionSetTitle(it["title"]!!.asText())
        }
        addAction("SetValue", mapOf(
            "key" to Parameter("string"),
            "value" to Parameter("string")
        ), null) { 
            actionSetValue(it["key"]!!.asText(), it["value"]!!.asText())
        }

        server.start(8888)
    }

    override fun behaviour() = super.behaviour().and(act {

        // increment step, hide message after set number of steps
        every(Duration.ofSeconds(1)) {
            if (step++ == stepClear) {
                message = ""
            }
        }

    })

    fun actionShowMessage(message: String, seconds: Int) {
        this.message = message
        stepClear = step + seconds
    }

    fun actionGetValue(key: String): String {
        return valuesMap[key] ?: "(unknown)"
    }

    fun actionSetValue(key: String, value: String) {
        valuesMap[key] = value
    }

    fun actionSetTitle(title: String) {
        this.title = title
    }

}
