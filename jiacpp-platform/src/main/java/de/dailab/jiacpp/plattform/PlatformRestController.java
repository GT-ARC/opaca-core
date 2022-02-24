package de.dailab.jiacpp.plattform;

import de.dailab.jiacpp.api.RuntimePlatformApi;
import de.dailab.jiacpp.model.*;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Map;


/**
 * REST controller for the JIAC++ Runtime Platform API. This class only defines the REST endpoints,
 * handles security etc. (once that's implemented); the actual logic is implemented elsewhere.
 */
@RestController
public class PlatformRestController implements RuntimePlatformApi {

	RuntimePlatformApi implementation = PlatformImpl.INSTANCE;

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
	public RuntimePlatform getInfo() {
		return implementation.getInfo();
	}

	/*
	 * AGENTS ROUTES
	 */

	@RequestMapping(value="/agents", method=RequestMethod.GET)
	@Override
	public List<AgentDescription> getAgents() {
		return implementation.getAgents();
	}

	@RequestMapping(value="/agents/{agentId}", method=RequestMethod.GET)
	@Override
	public AgentDescription getAgent(
			@PathVariable String agentId
	) {
		return implementation.getAgent(agentId);
	}

	@RequestMapping(value="/send/{agentId}", method=RequestMethod.POST)
	@Override
	public void send(
			@PathVariable String agentId,
			@RequestBody Message message
	) {
		implementation.send(agentId, message);
	}

	@RequestMapping(value="/broadcast/{channel}", method=RequestMethod.POST)
	@Override
	public void broadcast(
			@PathVariable String channel,
			@RequestBody Message message
	) {
		implementation.broadcast(channel, message);
	}

	@RequestMapping(value="/invoke/{action}", method=RequestMethod.POST)
	@Override
	public Object invoke(
			@PathVariable String action,
			@RequestBody Map<String, Object> parameters
	) {
		return implementation.invoke(action, parameters);
	}

	@RequestMapping(value="/invoke/{action}/{agentId}", method=RequestMethod.POST)
	@Override
	public Object invoke(
			@PathVariable String agentId,
			@PathVariable String action,
			@RequestBody Map<String, Object> parameters
	) {
		return implementation.invoke(agentId, action, parameters);
	}

	/*
	 * CONTAINERS ROUTES
	 */

	@RequestMapping(value="/containers", method=RequestMethod.POST)
	@Override
	public String addContainer(
			@RequestBody AgentContainerImage container
	) {
		return implementation.addContainer(container);
	}

	@RequestMapping(value="/containers", method=RequestMethod.GET)
	@Override
	public List<AgentContainer> getContainers() {
		return implementation.getContainers();
	}

	@RequestMapping(value="/containers/{containerId}", method=RequestMethod.GET)
	@Override
	public AgentContainer getContainer(
			@PathVariable String containerId
	) {
		return implementation.getContainer(containerId);
	}

	@RequestMapping(value="/containers/{containerId}", method=RequestMethod.DELETE)
	@Override
	public boolean removeContainer(
			@PathVariable String containerId
	) {
		return implementation.removeContainer(containerId);
	}

	/*
	 * CONNECTIONS ROUTES
	 */

	@RequestMapping(value="/connections", method=RequestMethod.POST)
	@Override
	public boolean connectPlatform(
			@RequestBody String url
	) {
		return implementation.connectPlatform(url);
	}

	@RequestMapping(value="/connections", method=RequestMethod.GET)
	@Override
	public List<String> getConnections() {
		return implementation.getConnections();
	}

	@RequestMapping(value="/connections", method=RequestMethod.DELETE)
	@Override
	public boolean disconnectPlatform(
			@RequestBody String url
	) {
		return implementation.disconnectPlatform(url);
	}
}
