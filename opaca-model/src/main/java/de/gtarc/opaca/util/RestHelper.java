package de.gtarc.opaca.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import de.gtarc.opaca.model.ErrorResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.java.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helper class for issuing different REST calls in Java.
 */
@Log
@AllArgsConstructor
public class RestHelper {

    public final String baseUrl;
    public final String token;

    public static final ObjectMapper mapper = JsonMapper.builder()
            .findAndAddModules().build();


    public <T> T get(String path, Class<T> type) throws IOException {
        var stream = request("GET", path, null);
        return type == null ? null : mapper.readValue(stream, type);
    }

    public <T> T post(String path, Object payload, Class<T> type) throws IOException {
        var stream = request("POST", path, payload);
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

    public void streamRequest(String method, String path, byte[] payload) throws IOException {
        // TODO find a way to unify this with "request" below, maybe with a callback to serialize the payload?
        HttpURLConnection connection = (HttpURLConnection) URI.create(baseUrl + path).toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        if (token != null && !token.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }

        connection.setDoOutput(true);
        connection.connect();

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

    public InputStream request(String method, String path, Object payload) throws IOException {
        log.info(String.format("%s %s%s (%s)", method, baseUrl, path, payload));
        HttpURLConnection connection = (HttpURLConnection) URI.create(baseUrl + path).toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        if (token != null && ! token.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        
        if (payload != null) {
            String json = mapper.writeValueAsString(payload);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            connection.connect();
            try (OutputStream os = connection.getOutputStream()) {
                os.write(bytes);
            }
        } else {
            connection.connect();
        }

        if (connection.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST) {
            return connection.getInputStream();
        } else {
            throw makeException(connection);
        }
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

    private IOException makeException(HttpURLConnection connection) throws IOException {
        var message = "Encountered an error when sending request to connected platform or container.";
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

}
