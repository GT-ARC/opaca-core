package de.dailab.jiacpp.sample

import de.dailab.hymas.model.MaterialInfo
import de.dailab.hymas.model.MesEvent
import de.dailab.hymas.model.ProposedAction
import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.behaviour.act

class SampleAgent: Agent(overrideName="mediator") {

    override fun behaviour() = act {

        // Receive MES Event from RestAgent (who in turn gets it from the "proper" HyMAS REST interface)
        // to be forwarded to proper Resource Agent
        on<MesEvent> {
            log.info("MA RECEIVED EVENT: $it")

            // get reference to the responsible resource agent

            when (it.eventType) {
                MesEvent.MesEventType.MATERIAL_ALLOCATED -> {

                    // TODO find responsible resource agent
                    val resourceId = 301

                    val ref = system.resolve("ra-$resourceId")
                    log.info("SENDING EVENT $it to $ref")
                    ref.tell(it)
                }
                else -> {
                    log.info("Don't know what to do with MES Event of Type ${it.eventType}")
                }
            }
        }

        // Receive Suggestion from a Resource Agent, to be forwarded to MES
        on<ProposedAction> {
            log.info("MA RECEIVED SUGGESTION: $it")
        }

        // TODO create classes like MaterialRequest to differentiate here
        respond<String, MaterialInfo> {
            log.info("ASKED FOR COIL")

            // get coil from MES and return it
            val mat = MaterialInfo()
            mat.matId = it
            mat
        }

    }

}
