package de.dailab.jiacpp.plattform;

import com.fasterxml.jackson.databind.JsonNode;
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
        // TODO assemble and return RuntimePlatform description
        return null;
    }

    /*
     * AGENTS ROUTES
     */

    @Override
    public List<AgentDescription> getAgents() {
        System.out.println("GET AGENTS");
        // TODO return list of all agents currently running in all the containers
        return null;
    }

    @Override
    public AgentDescription getAgent(String agentId) {
        System.out.println("GET AGENT");
        System.out.println(agentId);
        // TODO get agent container this agent is running in; get AgentDescription from that container
        return null;
    }

    @Override
    public void send(String agentId, Message message) {
        System.out.println("SEND");
        System.out.println(agentId);
        System.out.println(message);
        // TODO get agent container this agent is running in; forward REST call to container
    }

    @Override
    public void broadcast(String channel, Message message) {
        System.out.println("BROADCAST");
        System.out.println(channel);
        System.out.println(message);
        // TODO iterate containers, forward REST call to all containers
    }

    @Override
    public JsonNode invoke(String action, Map<String, JsonNode> parameters) {
        System.out.println("INVOKE");
        System.out.println(action);
        System.out.println(parameters);
        // TODO find (any) container providing this action, forward REST call to that container
        return null;
    }

    @Override
    public JsonNode invoke(String agentId, String action, Map<String, JsonNode> parameters) {
        System.out.println("INVOKE");
        System.out.println(action);
        System.out.println(agentId);
        System.out.println(parameters);
        // TODO find container with that agent, forward REST call to container
        // lookup registered containers for container including agent with agentid
        // check that this agent has action "action"
        // check that parameters match
        // invoke same REST Route on Container with that agent
        return null;
    }

    /*
     * CONTAINERS ROUTES
     */

    @Override
    public String addContainer(AgentContainerImage container) {
        System.out.println("ADD CONTAINER");
        System.out.println(container);
        // lookup container docker image
        // pull and start image
        // add container to internal containers table
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
        // TODO add url to "pending" set of remote platforms
        //  call connect-platform route of remote platform at that URL
        //  if if returns as okay, finally add to connected platforms
        //  if url already in pending list, do nothing (might be callback from other platform)
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
        // TODO similar to connect, forwarding to other platform, but then disconnect
        return false;
    }
}
