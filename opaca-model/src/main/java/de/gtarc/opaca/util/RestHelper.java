package de.gtarc.opaca.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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

    public ResponseEntity<StreamingResponseBody> getStream(String path) {
        StreamingResponseBody responseBody = response -> {
            InputStream stream = request("GET", path, null);
            int bytesRead;
            byte[] buffer = new byte[1024];
            try (BufferedInputStream bis = new BufferedInputStream(stream)) {
                while ((bytesRead = bis.read(buffer)) != -1) {
                    response.write(buffer, 0, bytesRead);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(responseBody);
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
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
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

        int responseCode = connection.getResponseCode();
        if (responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
            throw new IOException(String.format("%s: %s", responseCode, readStream(connection.getErrorStream())));
        }
    }

    public InputStream request(String method, String path, Object payload) throws IOException {
        log.info(String.format("%s %s%s (%s)", method, baseUrl, path, payload));
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
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
            throw new IOException(readStream(connection.getErrorStream()));
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

}
