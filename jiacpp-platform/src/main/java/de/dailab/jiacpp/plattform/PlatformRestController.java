package de.dailab.jiacpp.plattform;

import com.fasterxml.jackson.databind.JsonNode;
import de.dailab.jiacpp.api.RuntimePlatformApi;
import de.dailab.jiacpp.model.*;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.java.Log;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;


/**
 * REST controller for the JIAC++ Runtime Platform API. This class only defines the REST endpoints,
 * handles security etc. (once that's implemented); the actual logic is implemented elsewhere.
 */
@Log
@RestController
public class PlatformRestController implements RuntimePlatformApi {

	RuntimePlatformApi implementation = PlatformImpl.INSTANCE;

	/*
	 * LIFECYCLE
	 */

	@PostConstruct
	public void postConstruct() {
		log.info("In Post-Construct");
		// TODO read config/settings (tbd) with initial containers and connections to deploy and establish
	}

	@PreDestroy
	public void preDestroy() {
		log.info("In Destroy, stopping containers...");
		// disconnect remote platform, undeploy agent containers
		try {
			for (String connection : implementation.getConnections()) {
				implementation.disconnectPlatform(connection);
			}
			for (AgentContainer container : implementation.getContainers()) {
				implementation.removeContainer(container.getContainerId());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * INFO ROUTES
	 */

	@RequestMapping(value="/info", method=RequestMethod.GET)
	@Operation(summary="Get information on this Runtime Platform", tags={"info"})
	@Override
	public RuntimePlatform getInfo() throws IOException {
		log.info("Get Info");
		return implementation.getInfo();
	}

	/*
	 * AGENTS ROUTES
	 */

	@RequestMapping(value="/agents", method=RequestMethod.GET)
	@Operation(summary="Get List of Agents of all Agent Containers on this Platform", tags={"agents"})
	@Override
	public List<AgentDescription> getAgents() throws IOException {
		log.info("GET AGENTS");
		return implementation.getAgents();
	}

	@RequestMapping(value="/agents/{agentId}", method=RequestMethod.GET)
	@Operation(summary="Get Description of a single Agent", tags={"agents"})
	@Override
	public AgentDescription getAgent(
			@PathVariable String agentId
	) throws IOException {
		log.info(String.format("GET AGENT: %s", agentId));
		return implementation.getAgent(agentId);
	}

	@RequestMapping(value="/send/{agentId}", method=RequestMethod.POST)
	@Operation(summary="Send message to an Agent", tags={"agents"})
	@Override
	public void send(
			@PathVariable String agentId,
			@RequestBody Message message
	) throws IOException {
		log.info(String.format("SEND: %s, %s", agentId, message));
		implementation.send(agentId, message);
	}

	@RequestMapping(value="/broadcast/{channel}", method=RequestMethod.POST)
	@Operation(summary="Send broadcast message to all agents in all containers", tags={"agents"})
	@Override
	public void broadcast(
			@PathVariable String channel,
			@RequestBody Message message
	) throws IOException {
		log.info(String.format("BROADCAST: %s, %s", channel, message));
		implementation.broadcast(channel, message);
	}

	@RequestMapping(value="/invoke/{action}", method=RequestMethod.POST)
	@Operation(summary="Invoke action at any agent that provides it", tags={"agents"})
	@Override
	public JsonNode invoke(
			@PathVariable String action,
			@RequestBody Map<String, JsonNode> parameters
	) throws IOException {
		log.info(String.format("INVOKE: %s, %s", action, parameters));
		return implementation.invoke(action, parameters);
	}

	@RequestMapping(value="/invoke/{action}/{agentId}", method=RequestMethod.POST)
	@Operation(summary="Invoke action at that specific agent", tags={"agents"})
	@Override
	public JsonNode invoke(
			@PathVariable String agentId,
			@PathVariable String action,
			@RequestBody Map<String, JsonNode> parameters
	) throws IOException {
		log.info(String.format("INVOKE: %s, %s, %s", action, agentId, parameters));
		return implementation.invoke(agentId, action, parameters);
	}

	/*
	 * CONTAINERS ROUTES
	 */

	@RequestMapping(value="/containers", method=RequestMethod.POST)
	@Operation(summary="Start a new Agent Container on this platform", tags={"containers"})
	@Override
	public String addContainer(
			@RequestBody AgentContainerImage container
	) throws IOException {
		log.info(String.format("ADD CONTAINER: %s", container));
		return implementation.addContainer(container);
	}

	@RequestMapping(value="/containers", method=RequestMethod.GET)
	@Operation(summary="Get all Agent Containers running on this platform", tags={"containers"})
	@Override
	public List<AgentContainer> getContainers() throws IOException {
		log.info("GET CONTAINERS");
		return implementation.getContainers();
	}

	@RequestMapping(value="/containers/{containerId}", method=RequestMethod.GET)
	@Operation(summary="Get details on one specific Agent Container running on this platform", tags={"containers"})
	@Override
	public AgentContainer getContainer(
			@PathVariable String containerId
	) throws IOException {
		log.info(String.format("GET CONTAINER: %s", containerId));
		return implementation.getContainer(containerId);
	}

	@RequestMapping(value="/containers/{containerId}", method=RequestMethod.DELETE)
	@Operation(summary="Stop and remove Agent Container running on this platform", tags={"containers"})
	@Override
	public boolean removeContainer(
			@PathVariable String containerId
	) throws IOException {
		log.info(String.format("REMOVE CONTAINER: %s", containerId));
		return implementation.removeContainer(containerId);
	}

	/*
	 * CONNECTIONS ROUTES
	 */

	@RequestMapping(value="/connections", method=RequestMethod.POST)
	@Operation(summary="Establish connection to another Runtime Platform", tags={"connections"})
	@Override
	public boolean connectPlatform(
			@RequestBody String url
	) throws IOException {
		log.info(String.format("CONNECT PLATFORM: %s", url));
		return implementation.connectPlatform(url);
	}

	@RequestMapping(value="/connections", method=RequestMethod.GET)
	@Operation(summary="Get list of connected Runtime Platforms", tags={"connections"})
	@Override
	public List<String> getConnections() throws IOException {
		log.info("GET CONNECTIONS");
		return implementation.getConnections();
	}

	@RequestMapping(value="/connections", method=RequestMethod.DELETE)
	@Operation(summary="Remove connection to another Runtime Platform", tags={"connections"})
	@Override
	public boolean disconnectPlatform(
			@RequestBody String url
	) throws IOException {
		log.info(String.format("DISCONNECT PLATFORM: %s", url));
		return implementation.disconnectPlatform(url);
	}
}
