package de.dailab.jiacpp.plattform;

import com.fasterxml.jackson.databind.JsonNode;
import de.dailab.jiacpp.api.RuntimePlatformApi;
import de.dailab.jiacpp.model.*;
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
@RestController
public class PlatformRestController implements RuntimePlatformApi {

	RuntimePlatformApi implementation = AgentProxy.INSTANCE;

	/*
	 * LIFECYCLE
	 */

	@PostConstruct
	public void postConstruct() {
		System.out.println("Code on starup goes here");
	}

	@PreDestroy
	public void preDestroy() {
		System.out.println("Code on shutdown goes here");
		// TODO disconnect remote platform, undeploy agent containers
	}

	/*
	 * INFO ROUTES
	 */

	@RequestMapping(value="/info", method=RequestMethod.GET)
	@Override
	public RuntimePlatform getInfo() throws IOException {
		return implementation.getInfo();
	}

	/*
	 * AGENTS ROUTES
	 */

	@RequestMapping(value="/agents", method=RequestMethod.GET)
	@Override
	public List<AgentDescription> getAgents() throws IOException {
		return implementation.getAgents();
	}

	@RequestMapping(value="/agents/{agentId}", method=RequestMethod.GET)
	@Override
	public AgentDescription getAgent(
			@PathVariable String agentId
	) throws IOException {
		return implementation.getAgent(agentId);
	}

	@RequestMapping(value="/send/{agentId}", method=RequestMethod.POST)
	@Override
	public void send(
			@PathVariable String agentId,
			@RequestBody Message message
	) throws IOException {
		implementation.send(agentId, message);
	}

	@RequestMapping(value="/broadcast/{channel}", method=RequestMethod.POST)
	@Override
	public void broadcast(
			@PathVariable String channel,
			@RequestBody Message message
	) throws IOException {
		implementation.broadcast(channel, message);
	}

	@RequestMapping(value="/invoke/{action}", method=RequestMethod.POST)
	@Override
	public Object invoke(
			@PathVariable String action,
			@RequestBody Map<String, JsonNode> parameters
	) throws IOException {
		return implementation.invoke(action, parameters);
	}

	@RequestMapping(value="/invoke/{action}/{agentId}", method=RequestMethod.POST)
	@Override
	public Object invoke(
			@PathVariable String agentId,
			@PathVariable String action,
			@RequestBody Map<String, JsonNode> parameters
	) throws IOException {
		return implementation.invoke(agentId, action, parameters);
	}

	/*
	 * CONTAINERS ROUTES
	 */

	@RequestMapping(value="/containers", method=RequestMethod.POST)
	@Override
	public String addContainer(
			@RequestBody AgentContainerImage container
	) throws IOException {
		return implementation.addContainer(container);
	}

	@RequestMapping(value="/containers", method=RequestMethod.GET)
	@Override
	public List<AgentContainer> getContainers() throws IOException {
		return implementation.getContainers();
	}

	@RequestMapping(value="/containers/{containerId}", method=RequestMethod.GET)
	@Override
	public AgentContainer getContainer(
			@PathVariable String containerId
	) throws IOException {
		return implementation.getContainer(containerId);
	}

	@RequestMapping(value="/containers/{containerId}", method=RequestMethod.DELETE)
	@Override
	public boolean removeContainer(
			@PathVariable String containerId
	) throws IOException {
		return implementation.removeContainer(containerId);
	}

	/*
	 * CONNECTIONS ROUTES
	 */

	@RequestMapping(value="/connections", method=RequestMethod.POST)
	@Override
	public boolean connectPlatform(
			@RequestBody String url
	) throws IOException {
		return implementation.connectPlatform(url);
	}

	@RequestMapping(value="/connections", method=RequestMethod.GET)
	@Override
	public List<String> getConnections() throws IOException {
		return implementation.getConnections();
	}

	@RequestMapping(value="/connections", method=RequestMethod.DELETE)
	@Override
	public boolean disconnectPlatform(
			@RequestBody String url
	) throws IOException {
		return implementation.disconnectPlatform(url);
	}
}
