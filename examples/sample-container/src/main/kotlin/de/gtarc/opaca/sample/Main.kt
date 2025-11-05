package de.gtarc.opaca.sample

import de.dailab.jiacvi.communication.LocalBroker
import de.dailab.jiacvi.dsl.agentSystem
import de.gtarc.opaca.container.ContainerAgent
import de.gtarc.opaca.container.LoginHandler
import de.gtarc.opaca.container.LoginStatus
import de.gtarc.opaca.model.Login
import de.gtarc.opaca.util.ConfigLoader

object loginHandler: LoginHandler<String>() {
    private val logins = mutableMapOf<String, String>()
    override fun handleLogin(loginToken: String, credentials: Login): LoginStatus {
        // this does not make sense and is here just for checking those cases in unit tests
        if (credentials.username == "invalid") return LoginStatus.INVALID
        if (credentials.username == "not-supported") return LoginStatus.NOT_SUPPORTED
        // regular case
        logins[loginToken] = credentials.username
        return LoginStatus.ACCEPTED
    }
    override fun handleLogout(loginToken: String?) = logins.remove(loginToken) != null
    override fun get(loginToken: String?) = logins.get(loginToken)
}

fun main() {
    val image = ConfigLoader.loadContainerImageFromResources("/sample-image.json")
    agentSystem("opaca-sample-container") {
        enable(LocalBroker)
        agents {
            add(ContainerAgent(image, true, loginHandler))
            add(SampleAgent("sample1", loginHandler))
            add(SampleAgent("sample2", loginHandler))
            add(SimpleUIAgent())
            add(SimpleUDPAgent())
        }
    }.start()
}
