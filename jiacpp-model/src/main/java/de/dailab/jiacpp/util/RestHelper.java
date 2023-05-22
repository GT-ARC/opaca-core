package de.dailab.jiacpp.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.AllArgsConstructor;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for issuing different REST calls in Java.
 */
@AllArgsConstructor
public class RestHelper {

    public final String baseUrl;

    public static final ObjectMapper mapper = JsonMapper.builder()
            .findAndAddModules().build();


    public <T> T post(String path, String usernamePlatform, String passwordPlatform, Class<T> type) throws IOException {
        // Create a payload object or map to hold the username and password
        Map<String, String> payload = new HashMap<>();
        payload.put("username", usernamePlatform);
        payload.put("password", passwordPlatform);
    
        // Call the request method with the POST method and the payload
        return request("POST", path, payload, type);
    }

    public <T> T get(String path, Class<T> type) throws IOException {
        return request("GET", path, null, type);
    }

    public <T> T post(String path, Object payload, Class<T> type) throws IOException {
        return request("POST", path, payload, type);
    }

    public <T> T delete(String path, Object payload, Class<T> type) throws IOException {
        return request("DELETE", path, payload, type);
    }

    public <T> T request(String method, String path, Object payload, Class<T> type) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod(method);

        if (payload != null) {
            String json = mapper.writeValueAsString(payload);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.connect();
            try (OutputStream os = connection.getOutputStream()) {
                os.write(bytes);
            }
        } else {
            connection.connect();
        }

        if (type != null) {
            return mapper.readValue(connection.getInputStream(), type);
        } else {
            return null;
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

}
