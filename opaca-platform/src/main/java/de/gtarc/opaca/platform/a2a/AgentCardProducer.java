package de.gtarc.opaca.platform.a2a;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;

import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class AgentCardProducer {

    private static final String AGENT_URL = "http://localhost:10001";

    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        return AgentCard.builder()
                .name("OPACA Agent")
                .description("")
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), AGENT_URL)
                ))
                .version("1.0.0")
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .defaultInputModes(Collections.singletonList("text"))
                .defaultOutputModes(Collections.singletonList("text"))
                .skills(Collections.singletonList(AgentSkill.builder()
                        .id("opaca_actions")
                        .name("OPACA Actions")
                        .description("""
                        Allows to invoke Actions provided by Agents running in Agent Containers on this Platform.
                        Exact capabilities depend on the containers that are currently started.
                        """)
                        .tags(Collections.singletonList("opaca"))
                        .examples(List.of(
                                "Use the Home Assistant Agent to get the temperature in the kitchen.",
                                "Find a route by train from Berlin Ernst-Reuter-Platz to Hauptbahnhof."
                        ))
                        .build()))
                .build();
    }
}
