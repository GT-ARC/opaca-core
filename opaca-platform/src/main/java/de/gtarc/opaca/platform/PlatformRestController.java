package de.gtarc.opaca.platform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import de.gtarc.opaca.api.RuntimePlatformApi;
import de.gtarc.opaca.model.*;
import de.gtarc.opaca.platform.ActionToOpenApi.ActionFormat;
import de.gtarc.opaca.util.EventHistory;
import de.gtarc.opaca.util.RestHelper.RequestException;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.annotation.PostConstruct;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;


/**
 * REST controller for the OPACA Runtime Platform API. This class only defines the REST endpoints,
 * handles security etc. (once that's implemented); the actual logic is implemented elsewhere.
 */
@Log
@RestController
@SecurityRequirement(name = "bearerAuth")
@CrossOrigin(origins = "*", allowedHeaders = "*",
		methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS } )
public class PlatformRestController {

	@Autowired
	private RuntimePlatformApi implementation;

	@Autowired
	private PlatformConfig config;


	/*
	 * LIFECYCLE
	 */

	@PostConstruct
	public void postConstruct() {
		EventHistory.maxSize = config.eventHistorySize;
	}

	/*
	 * GENERIC/AUTOMATIC EXCEPTION HANDLING
	 */

	@ExceptionHandler(value=NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException e) {
		log.warning(e.getMessage());  // probably a user error
		return makeErrorResponse(HttpStatus.NOT_FOUND, e, null);
	}

	@ExceptionHandler(value=JsonProcessingException.class)
	@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
	public ResponseEntity<ErrorResponse> handleJsonException(JsonProcessingException e) {
		log.warning(e.getMessage());  // user error
		return makeErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, e, null);
	}

	@ExceptionHandler(value=RequestException.class)
	@ResponseStatus(HttpStatus.BAD_GATEWAY)
	public ResponseEntity<ErrorResponse> handleRequestException(RequestException e) {
		log.severe(e.getMessage());
		return makeErrorResponse(HttpStatus.BAD_GATEWAY, e, e.getNestedError());
	}

	@ExceptionHandler(value=IOException.class)
	@ResponseStatus(HttpStatus.BAD_GATEWAY)
	public ResponseEntity<ErrorResponse> handleIoException(IOException e) {
		log.severe(e.getMessage());  // should not happen (but can also be user error)
		return makeErrorResponse(HttpStatus.BAD_GATEWAY, e, null);
	}

	@ExceptionHandler(value=IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
		log.warning(e.getMessage());  // probably user error
		return makeErrorResponse(HttpStatus.BAD_REQUEST, e, null);
	}

	private ResponseEntity<ErrorResponse> makeErrorResponse(HttpStatus statusCode, Exception error, ErrorResponse nestedError) {
		var content = new ErrorResponse(statusCode.value(), error.getMessage(), nestedError);
		return ResponseEntity.status(statusCode).body(content);
	}

	/*
	 * "LANDING PAGE"
	 */

	@RequestMapping(value="/", method=RequestMethod.GET)
	@Hidden
	public String landingPage() throws IOException {
		try (var inputstream = PlatformRestController.class.getResourceAsStream("/static/index.html")) {
			return new String(inputstream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/*
	 * AUTHENTICATION
	 */

	@RequestMapping(value="/login", method=RequestMethod.POST)
	@Operation(summary="Login with username and password", tags={"authentication"})
	public String login(
			@RequestBody Login loginParams
	) throws IOException {
		return implementation.login(loginParams);
	}

	@RequestMapping(value="/token", method=RequestMethod.GET)
	@Operation(summary="Renew token for logged in user.", tags={"authentication"})
	public String renewToken() throws IOException {
		return implementation.renewToken();
	}

	/*
	 * INFO ROUTES
	 */

	@RequestMapping(value="/info", method=RequestMethod.GET)
	@Operation(summary="Get information on this Runtime Platform", tags={"info"})
	public RuntimePlatform getPlatformInfo() throws IOException {
		log.info("Get Info");
		return implementation.getPlatformInfo();
	}

	@RequestMapping(value="/config", method=RequestMethod.GET)
	@Operation(summary="Get Configuration of this Runtime Platform", tags={"info"})
	public Map<String, ?> getPlatformConfig() throws IOException {
		log.info("Get Config");
		return implementation.getPlatformConfig();
	}

	@RequestMapping(value="/history", method=RequestMethod.GET)
	@Operation(summary="Get history on this Runtime Platform", tags={"info"})
	public List<Event> getHistory() throws IOException {
		log.info("Get History");
		return implementation.getHistory();
	}

	@RequestMapping(value="v3/api-docs/actions", method = RequestMethod.GET)
	@Operation(summary = "Get an Open-API compliant list of all agent actions currently available on this Platform", tags={"info"}, hidden=true)
	public String getOpenApiActions(
			@RequestParam(required = false, defaultValue = "JSON") ActionFormat format
	) throws IOException {
		log.info("Get Actions");
		return ActionToOpenApi.createOpenApiSchema(implementation.getContainers(), format, config.enableAuth);
	}

	/*
	 * AGENTS ROUTES
	 */

	@RequestMapping(value="/agents", method=RequestMethod.GET)
	@Operation(summary="Get List of Agents of all Agent Containers on this Platform", tags={"agents"})
	public List<AgentDescription> getAgents(
		@RequestParam(required = false, defaultValue = "false") boolean includeConnected
	) throws IOException {
		log.info("GET AGENTS");
		return includeConnected ? implementation.getAllAgents() : implementation.getAgents();
	}

	@RequestMapping(value="/agents/{agentId}", method=RequestMethod.GET)
	@Operation(summary="Get Description of a single Agent; null if agent not found", tags={"agents"})
	public AgentDescription getAgent(
			@PathVariable String agentId
	) throws IOException {
		log.info(String.format("GET AGENT: %s", agentId));
		return implementation.getAgent(agentId);
	}

	@RequestMapping(value="/send/{agentId}", method=RequestMethod.POST)
	@Operation(summary="Send message to an Agent", tags={"agents"})
	public void send(
			@PathVariable String agentId,
			@RequestBody Message message,
			@RequestParam(required = false) String containerId,
			@RequestParam(required = false, defaultValue = "true") boolean forward
	) throws IOException {
		log.info(String.format("SEND: %s, %s", agentId, message));
		implementation.send(agentId, message, containerId, forward);
	}

	@RequestMapping(value="/broadcast/{channel}", method=RequestMethod.POST)
	@Operation(summary="Send broadcast message to all agents in all containers", tags={"agents"})
	public void broadcast(
			@PathVariable String channel,
			@RequestBody Message message,
			@RequestParam(required = false) String containerId,
			@RequestParam(required = false, defaultValue = "true") boolean forward
	) throws IOException {
		log.info(String.format("BROADCAST: %s, %s", channel, message));
		implementation.broadcast(channel, message, containerId, forward);
	}

	@RequestMapping(value="/invoke/{action}", method=RequestMethod.POST)
	@Operation(summary="Invoke action at any agent that provides it", tags={"agents"})
	public JsonNode invoke(
			@PathVariable String action,
			@RequestBody Map<String, JsonNode> parameters,
			@RequestParam(required = false, defaultValue = "-1") int timeout,
			@RequestParam(required = false) String containerId,
			@RequestParam(required = false, defaultValue = "true") boolean forward
	) throws IOException {
		log.info(String.format("INVOKE: %s, %s", action, parameters));
		return implementation.invoke(action, parameters, null, timeout, containerId, forward);
	}

	@RequestMapping(value="/invoke/{action}/{agentId}", method=RequestMethod.POST)
	@Operation(summary="Invoke action at that specific agent", tags={"agents"})
	public JsonNode invoke(
			@PathVariable String action,
			@RequestBody Map<String, JsonNode> parameters,
			@PathVariable String agentId,
			@RequestParam(required = false, defaultValue = "-1") int timeout,
			@RequestParam(required = false) String containerId,
			@RequestParam(required = false, defaultValue = "true") boolean forward
	) throws IOException {
		log.info(String.format("INVOKE: %s, %s, %s", action, agentId, parameters));
		return implementation.invoke(action, parameters, agentId, timeout, containerId, forward);
	}

	@RequestMapping(value="/stream/{stream}", method=RequestMethod.GET)
	@Operation(summary="Get named data stream from any agent that provides it", tags={"agents"})
	public ResponseEntity<StreamingResponseBody> getStream(
			@PathVariable String stream,
			@RequestParam(required = false) String containerId,
			@RequestParam(required = false, defaultValue = "true") boolean forward
	) throws IOException {
		log.info(String.format("STREAM: %s ", stream));
		return wrapStream(implementation.getStream(stream, null, containerId, forward));
	}

	@RequestMapping(value="/stream/{stream}/{agentId}", method=RequestMethod.GET)
	@Operation(summary="Get named data stream from a specific agent", tags={"agents"})
	public ResponseEntity<StreamingResponseBody> getStream(
			@PathVariable String stream,
			@PathVariable String agentId,
			@RequestParam(required = false) String containerId,
			@RequestParam(required = false, defaultValue = "true") boolean forward
	) throws IOException {
		log.info(String.format("STREAM: %s, %s", stream, agentId));
		return wrapStream(implementation.getStream(stream, agentId, containerId, forward));
	}

	@RequestMapping(value="/stream/{stream}", method=RequestMethod.POST)
	@Operation(summary="Post named data stream to any agent that accepts it", tags={"agents"})
    public void postStream(
            @PathVariable String stream,
            @RequestBody(required = false) byte[] inputStream,
            @RequestParam(required = false) String containerId,
            @RequestParam(required = false, defaultValue = "true") boolean forward
    ) throws IOException {
        log.info(String.format("POST STREAM: %s ", stream));
        implementation.postStream(stream, inputStream, null, containerId, forward);
    }

	@RequestMapping(value="/stream/{stream}/{agentId}", method=RequestMethod.POST)
	@Operation(summary="Post named data stream to a specific agent", tags={"agents"})
    public void postStream(
            @PathVariable String stream,
			@RequestBody(required = false) byte[] inputStream,
            @PathVariable String agentId,
            @RequestParam(required = false) String containerId,
            @RequestParam(required = false, defaultValue = "true") boolean forward
    ) throws IOException {
        log.info(String.format("POST STREAM: %s, %s", stream, agentId));
        implementation.postStream(stream, inputStream, agentId, containerId, forward);
    }

	/*
	 * CONTAINERS ROUTES
	 */

	@RequestMapping(value="/containers", method=RequestMethod.POST)
	@Operation(summary="Start a new Agent Container on this platform", tags={"containers"})
	public String addContainer(
			@RequestBody PostAgentContainer container
	) throws IOException {
		log.info(String.format("ADD CONTAINER: %s", container));
		return implementation.addContainer(container);
	}

	@RequestMapping(value="/containers", method=RequestMethod.PUT)
	@Operation(summary="Start a new Agent Container on this platform, replacing an existing container of the same image", tags={"containers"})
	public String updateContainer(
			@RequestBody PostAgentContainer container
	) throws IOException {
		log.info(String.format("UPDATE CONTAINER: %s", container));
		return implementation.updateContainer(container);
	}

	@RequestMapping(value="/containers", method=RequestMethod.GET)
	@Operation(summary="Get all Agent Containers running on this platform", tags={"containers"})
	public List<AgentContainer> getContainers() throws IOException {
		log.info("GET CONTAINERS");
		return implementation.getContainers();
	}

	@RequestMapping(value="/containers/{containerId}", method=RequestMethod.GET)
	@Operation(summary="Get details on one specific Agent Container running on this platform; null if not found", tags={"containers"})
	public AgentContainer getContainer(
			@PathVariable String containerId
	) throws IOException {
		log.info(String.format("GET CONTAINER: %s", containerId));
		return implementation.getContainer(containerId);
	}

	@RequestMapping(value="/containers/{containerId}", method=RequestMethod.DELETE)
	@Operation(summary="Stop and remove Agent Container running on this platform; " +
			"return false if container not found or already stopped", tags={"containers"})
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
	public boolean connectPlatform(
			@RequestBody LoginConnection loginConnection
	) throws IOException {
		// TODO handle IO Exception (platform not found or does not respond, could be either 404 or 502)
		log.info(String.format("CONNECT PLATFORM: %s", loginConnection.getUrl()));
		return implementation.connectPlatform(loginConnection);
	}

	@RequestMapping(value="/connections", method=RequestMethod.GET)
	@Operation(summary="Get list of connected Runtime Platforms", tags={"connections"})
	public List<String> getConnections() throws IOException {
		log.info("GET CONNECTIONS");
		return implementation.getConnections();
	}

	@RequestMapping(value="/connections", method=RequestMethod.DELETE)
	@Operation(summary="Remove connection to another Runtime Platform", tags={"connections"})
	public boolean disconnectPlatform(
			@RequestBody String url
	) throws IOException {
		log.info(String.format("DISCONNECT PLATFORM: %s", url));
		return implementation.disconnectPlatform(url);
	}

	/*
	 * NOTIFY ROUTES
	 */

	@RequestMapping(value="/containers/notify", method=RequestMethod.POST)
	@Operation(summary="Notify Platform about updates", tags={"containers"})
	public boolean notifyUpdateContainer(@RequestBody String containerId) throws IOException {
		log.info(String.format("NOTIFY: %s", containerId));
		return implementation.notifyUpdateContainer(containerId);
	}

	@RequestMapping(value="/connections/notify", method=RequestMethod.POST)
	@Operation(summary="Notify Platform about updates", tags={"connections"})
	public boolean notifyUpdatePlatform(@RequestBody String platformUrl) throws IOException {
		log.info(String.format("NOTIFY: %s", platformUrl));
		return implementation.notifyUpdatePlatform(platformUrl);
	}

	/*
	 * HELPER METHODS
	 */

	private ResponseEntity<StreamingResponseBody> wrapStream(InputStream stream) {
		StreamingResponseBody responseBody = stream::transferTo;
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(responseBody);
	}

}
