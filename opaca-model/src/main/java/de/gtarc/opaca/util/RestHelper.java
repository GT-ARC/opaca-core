package de.gtarc.opaca.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import de.gtarc.opaca.model.ErrorResponse;
import de.gtarc.opaca.model.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.java.Log;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Helper class for issuing different REST calls in Java. While this class can theoretically
 * be used for all kinds of REST or HTTP requests, is has been created especially for calling
 * OPACA API routes (at the AC by the RP, or at the RP by the AC), setting some headers and 
 * properties that are required for that, and logging certain requests in the OPACA Event History.
 */
@Log
@AllArgsConstructor
public class RestHelper {

    public final String baseUrl;

    public final String senderId;

    public final String token;

    public final Integer timeout;

    public RestHelper(String baseUrl) {
        this(baseUrl, null, null, null);
    }

    public RestHelper(String baseUrl, String senderId, String token) {
        this(baseUrl, senderId, token, null);
    }

    public static final ObjectMapper mapper = JsonMapper.builder()
            .findAndAddModules().build();


    public <T> T get(String path, Class<T> type) throws IOException {
        var stream = request("GET", path, null);
        return type == null ? null : mapper.readValue(stream, type);
    }

    public <T> T get(String path, TypeReference<T> type) throws IOException {
        var stream = request("GET", path, null);
        return type == null ? null : mapper.readValue(stream, type);
    }

    public <T> T post(String path, Object payload, Class<T> type) throws IOException {
        var stream = request("POST", path, payload);
        return type == null ? null : mapper.readValue(stream, type);
    }

    public <T> T put(String path, Object payload, Class<T> type) throws IOException {
        var stream = request("PUT", path, payload);
        return type == null ? null : mapper.readValue(stream, type);
    }

    public <T> T delete(String path, Object payload, Class<T> type) throws IOException {
        var stream = request("DELETE", path, payload);
        return type == null ? null : mapper.readValue(stream, type);
    }

    public void postStream(String path, byte[] inputStream) {
        try {
            streamRequest("POST", path, inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Variant of request that sends the payload as a stream. Currently only used for POST /stream route.
     * 
     * TODO further unify this with regular "request" method? can we _always_ send the payload as a stream?
     *      why does this method explicitly close the connection and the other doesn't? why does the other
     *      have an additional layer of try/catch? check what of that's really necessary.
     */
    public void streamRequest(String method, String path, byte[] payload) throws IOException {
        var connection = createConnection(method, path, null);

        connection.setDoOutput(true);
        connection.connect();

        createForwardEvent(method, path);

        try (OutputStream os = connection.getOutputStream();
            InputStream inputStream = new ByteArrayInputStream(payload);
            BufferedInputStream bis = new BufferedInputStream(inputStream)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        } finally {
            connection.disconnect();
        }

        if (connection.getResponseCode() >= HttpURLConnection.HTTP_BAD_REQUEST) {
            throw makeException(connection);
        }
    }

    public InputStream request(String method, String path, List<HttpCookie> cookies, Object payload) throws IOException {
        log.info(String.format("%s %s%s (%s)", method, baseUrl, path, payload));
        var connection = createConnection(method, path, cookies);

        try {
            if (payload != null) {
                String content = (payload instanceof String)
                        ? (String) payload
                        : mapper.writeValueAsString(payload);
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(bytes.length);
                connection.connect();
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(bytes);
                }
            } else {
                connection.connect();
            }
            
            createForwardEvent(method, path);

            if (connection.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST) {
                return connection.getInputStream();
            } else {
                throw makeException(connection);
            }
        } catch (SocketTimeoutException e) {
            throw makeException(connection);
        }
    }

    public InputStream request(String method, String path, Object payload) throws IOException {
        return request(method, path, null, payload);
    }

    /**
     * Create connection for given method and path with all the necessary properties and headers.
     */
    private HttpURLConnection createConnection(String method, String path, List<HttpCookie> cookies) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(baseUrl + path).toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        if (senderId != null && ! senderId.isEmpty()) {
            connection.setRequestProperty(Event.HEADER_SENDER_ID, senderId);
        }
        if (token != null && ! token.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (timeout != null && timeout > 0) {
            connection.setConnectTimeout(timeout);
        }
        if (cookies != null && !cookies.isEmpty()) {
            var cookieString = cookies.stream()
                    .map(c ->  c.getName() + "=" + c.getValue())
                    .collect(Collectors.joining("; "));
            connection.setRequestProperty("Cookie", cookieString);
        }

        return  connection;
    }

    public static JsonNode readJson(String json) throws IOException {
        return mapper.readTree(json);
    }

    public static Map<String, JsonNode> readMap(String json) throws IOException {
        TypeReference<Map<String, JsonNode>> prototype = new TypeReference<>() {};
        return mapper.readValue(json, prototype);
    }

    public static <T> T readObject(String json, Class<T> type) throws IOException {
        return mapper.readValue(json, type);
    }

    public static String writeJson(Object obj) throws IOException {
        return mapper.writeValueAsString(obj);
    }

    public String readStream(InputStream stream) {
        return stream == null ? null : new BufferedReader(new InputStreamReader(stream))
                .lines().collect(Collectors.joining("\n"));
    }

    protected IOException makeException(HttpURLConnection connection) throws IOException {
        var message = "Encountered an error when sending request to " + baseUrl;
        var response = readStream(connection.getErrorStream());
        try {
            var nestedError = mapper.readValue(response, ErrorResponse.class);
            return new RequestException(message, nestedError);
        } catch (JsonProcessingException e) {
            var nestedError = new ErrorResponse(connection.getResponseCode(), response, null);
            return new RequestException(message, nestedError);
        }
    }

    @Getter
    public static class RequestException extends IOException {

        final ErrorResponse nestedError;

        public RequestException(String message, ErrorResponse nestedError) {
            super(message);
            this.nestedError = nestedError;
        }
    }

    /**
     * Get the latest CALL Event with same method and add FORWARD event related to that, if any.
     * This does nothing if the Event History is empty, e.g. in the AgentContainer.
     */
    private void createForwardEvent(String method, String path) {
        var key = String.format("%s %s", method, path.split("\\?")[0]); // part before the query
        Optional<Event> related = EventHistory.getInstance().getEvents().stream()
                .filter(x -> x.getEventType() == Event.EventType.CALL && x.getRoute().equals(key))
                .max(Comparator.comparing(Event::getTimestamp));
        if (related.isPresent()) {
            Event event = new Event(Event.EventType.FORWARD, null, null, baseUrl, null, related.get().getId());
            EventHistory.getInstance().addEvent(event);
        }
    }

    /**
     * helper function in case the payload should be an urlencoded string
     * e.g.
     * var body = RestHelper.encodeUrlencoded(payload)
     * client.request("GET", "/some/path", body)
     */
    public static String encodeUrlencoded(Map<String, String> payload) {
        return payload.entrySet().stream()
                .map(e -> {
                    var name = URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8);
                    var value = URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8);
                    return name + "=" + value;
                }).collect(Collectors.joining("&"));
    }

}
