package de.dailab.jiacpp.plattform;

import de.dailab.jiacpp.api.RuntimePlatformApi;
import de.dailab.jiacpp.model.*;

import java.util.List;
import java.util.Map;

/**
 * This class provides the actual implementation of the API routes. Might also be split up
 * further, e.g. for agent-forwarding, container-management, and linking to other platforms.
 */
public class PlatformImpl implements RuntimePlatformApi {

    public static final RuntimePlatformApi INSTANCE = new PlatformImpl();


    @Override
    public RuntimePlatform getInfo() {
        System.out.println("GET INFO");
        return null;
    }

    /*
     * AGENTS ROUTES
     */

    @Override
    public List<AgentDescription> getAgents() {
        System.out.println("GET AGENTS");
        System.out.println();
        return null;
    }

    @Override
    public AgentDescription getAgent(String agentId) {
        System.out.println("GET AGENT");
        System.out.println(agentId);
        return null;
    }

    @Override
    public void send(String agentId, Message message) {
        System.out.println("SEND");
        System.out.println(agentId);
        System.out.println(message);
    }

    @Override
    public void broadcast(String channel, Message message) {
        System.out.println("BROADCAST");
        System.out.println(channel);
        System.out.println(message);
    }

    @Override
    public Object invoke(String action, Map<String, Object> parameters) {
        System.out.println("INVOKE");
        System.out.println(action);
        System.out.println(parameters);
        return null;
    }

    @Override
    public Object invoke(String agentId, String action, Map<String, Object> parameters) {
        System.out.println("INVOKE");
        System.out.println(action);
        System.out.println(agentId);
        System.out.println(parameters);
        return null;
    }

    /*
     * CONTAINERS ROUTES
     */

    @Override
    public String addContainer(AgentContainerImage container) {
        System.out.println("ADD CONTAINER");
        System.out.println(container);
        return null;
    }

    @Override
    public List<AgentContainer> getContainers() {
        System.out.println("GET CONTAINERS");
        return null;
    }

    @Override
    public AgentContainer getContainer(String containerId) {
        System.out.println("GET CONTAINER");
        System.out.println(containerId);
        return null;
    }

    @Override
    public boolean removeContainer(String containerId) {
        System.out.println("REMOVE CONTAINER");
        System.out.println(containerId);
        return false;
    }

    /*
     * CONNECTIONS ROUTES
     */

    @Override
    public boolean connectPlatform(String url) {
        System.out.println("CONNECT PLATFORM");
        System.out.println(url);
        return false;
    }

    @Override
    public List<String> getConnections() {
        System.out.println("GET CONNECTIONS");
        return null;
    }

    @Override
    public boolean disconnectPlatform(String url) {
        System.out.println("DISCONNECT PLATFORM");
        System.out.println(url);
        return false;
    }
}
