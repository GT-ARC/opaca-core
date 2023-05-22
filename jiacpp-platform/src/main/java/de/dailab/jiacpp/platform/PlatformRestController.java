package de.dailab.jiacpp.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import de.dailab.jiacpp.api.RuntimePlatformApi;
import de.dailab.jiacpp.model.*;
import de.dailab.jiacpp.util.*;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;


/**
 * REST controller for the JIAC++ Runtime Platform API. This class only defines the REST endpoints,
 * handles security etc. (once that's implemented); the actual logic is implemented elsewhere.
 */
@Log
@RestController
public class PlatformRestController implements RuntimePlatformApi {

	@Autowired
	PlatformConfig config;


	RuntimePlatformApi implementation;


	/*
	 * LIFECYCLE
	 */

	@PostConstruct
	public void postConstruct() {
		log.info("In Post-Construct");
		log.info("Started with Config: " + config);
		implementation = EventProxy.create(new PlatformImpl(config));
		this.startDefaultImages();
	}

	@PreDestroy
	public void preDestroy() throws IOException {
		log.info("In Destroy, stopping containers...");
		for (String connection : implementation.getConnections()) {
			try {
				implementation.disconnectPlatform(connection);
			} catch (Exception e) {
				log.warning("Exception disconnecting from " + connection + ": " + e.getMessage());
			}
		}
		for (AgentContainer container : implementation.getContainers()) {
			try {
				implementation.removeContainer(container.getContainerId());
			} catch (Exception e) {
				log.warning("Exception stopping container " + container.getContainerId() + ": " + e.getMessage());
			}
		}
	}

	/*
	 * GENERIC/AUTOMATIC EXCEPTION HANDLING
	 */

	@ExceptionHandler(value=NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ResponseEntity<String> handleNotFound(NoSuchElementException e) {
		log.warning(e.getMessage());  // probably a user error
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	}

	@ExceptionHandler(value=JsonProcessingException.class)
	@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
	public ResponseEntity<String> handleJsonException(JsonProcessingException e) {
		log.warning(e.getMessage());  // user error
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(e.getMessage());
	}

	@ExceptionHandler(value=IOException.class)
	@ResponseStatus(HttpStatus.BAD_GATEWAY)
	public ResponseEntity<String> handleIoException(IOException e) {
		log.severe(e.getMessage());  // should not happen
		e.printStackTrace();
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(e.getMessage());
	}

	/*
	 * INFO ROUTES
	 */

	@RequestMapping(value="/info", method=RequestMethod.GET)
	@Operation(summary="Get information on this Runtime Platform", tags={"info"})
	@Override
	public RuntimePlatform getPlatformInfo() throws IOException {
		log.info("Get Info");
		return implementation.getPlatformInfo();
	}

	@RequestMapping(value="/history", method=RequestMethod.GET)
	@Operation(summary="Get history on this Runtime Platform", tags={"info"})
	@Override
	public List<Event> getHistory() throws IOException {
		log.info("Get History");
		return implementation.getHistory();
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
	@Operation(summary="Get Description of a single Agent; null if agent not found", tags={"agents"})
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
			@RequestBody Message message,
			@RequestParam(required = false, defaultValue = "true") boolean forward
	) throws IOException {
		log.info(String.format("SEND: %s, %s", agentId, message));
		implementation.send(agentId, message, forward);
	}

	@RequestMapping(value="/broadcast/{channel}", method=RequestMethod.POST)
	@Operation(summary="Send broadcast message to all agents in all containers", tags={"agents"})
	@Override
	public void broadcast(
			@PathVariable String channel,
			@RequestBody Message message,
			@RequestParam(required = false, defaultValue = "true") boolean forward
	) throws IOException {
		log.info(String.format("BROADCAST: %s, %s, %s", channel, message, forward));
		implementation.broadcast(channel, message, forward);
	}

	@RequestMapping(value="/invoke/{action}", method=RequestMethod.POST)
	@Operation(summary="Invoke action at any agent that provides it", tags={"agents"})
	@Override
	public JsonNode invoke(
			@PathVariable String action,
			@RequestBody Map<String, JsonNode> parameters,
			@RequestParam(required = false, defaultValue = "true") boolean forward
	) throws IOException {
		log.info(String.format("INVOKE: %s, %s", action, parameters));
		return implementation.invoke(action, parameters, forward);
	}

	@RequestMapping(value="/invoke/{action}/{agentId}", method=RequestMethod.POST)
	@Operation(summary="Invoke action at that specific agent", tags={"agents"})
	@Override
	public JsonNode invoke(
			@PathVariable String agentId,
			@PathVariable String action,
			@RequestBody Map<String, JsonNode> parameters,
			@RequestParam(required = false, defaultValue = "true") boolean forward
	) throws IOException {
		log.info(String.format("INVOKE: %s, %s, %s", action, agentId, parameters));
		return implementation.invoke(agentId, action, parameters, forward);
	}


	@RequestMapping(value = "/login", method = RequestMethod.POST)
		@Operation(summary = "Login with username and password", tags = {"authentication"})
		@Override
		public String login(
				@RequestParam String username,
				@RequestParam String password
		) throws IOException {
			return implementation.login(username, password);
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
		// TODO handle "failed to start container" error (tbd)
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
	@Operation(summary="Get details on one specific Agent Container running on this platform; null if not found", tags={"containers"})
	@Override
	public AgentContainer getContainer(
			@PathVariable String containerId
	) throws IOException {
		log.info(String.format("GET CONTAINER: %s", containerId));
		return implementation.getContainer(containerId);
	}

	@RequestMapping(value="/containers/{containerId}", method=RequestMethod.DELETE)
	@Operation(summary="Stop and remove Agent Container running on this platform; " +
			"return false if container not found or already stopped", tags={"containers"})
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
	@Operation(summary="Establish connection to another Runtime Platform; " +
			"return false if platform already connected", tags={"connections"})
	@Override
	public boolean connectPlatform(
			@RequestBody String url
	) throws IOException {
		// TODO handle IO Exception (platform not found or does not respond, could be either 404 or 502)
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


	@RequestMapping(value="/containers/notify", method=RequestMethod.POST)
	@Operation(summary="Notify Platform about updates", tags={"containers"})
	@Override
	public boolean notifyUpdateContainer(@RequestBody String containerId) throws IOException {
		log.info(String.format("NOTIFY: %s", containerId));
		return implementation.notifyUpdateContainer(containerId);
	}

	@RequestMapping(value="/connections/notify", method=RequestMethod.POST)
	@Operation(summary="Notify Platform about updates", tags={"connections"})
	@Override
	public boolean notifyUpdatePlatform(@RequestBody String platformUrl) throws IOException {
		log.info(String.format("NOTIFY: %s", platformUrl));
		return implementation.notifyUpdatePlatform(platformUrl);
	}

	/*
	 * HELPER METHODS
	 */

	/**
	 * reads image config files from default container directory
	 */
	public List<File> readDefaultImages() {
		if (Strings.isNullOrEmpty(config.defaultImageDirectory)) return List.of();
		try {
			return Files.list(Path.of(config.defaultImageDirectory))
					.map(Path::toFile)
					.filter(f -> f.isFile() && f.getName().toLowerCase().endsWith(".json"))
					.collect(Collectors.toList());
		} catch (IOException e) {
			log.severe("Failed to read default images: " + e);
			return List.of();
		}
	}

	private void startDefaultImages() {
		for (File file: readDefaultImages()) {
			log.info("Auto-deploying " + file);
			try {
				this.addContainer(RestHelper.mapper.readValue(file, AgentContainerImage.class));
			} catch (Exception e) {
				log.severe(String.format("Failed to load image specified in file %s: %s", file, e));
			}
		}
	}

}
