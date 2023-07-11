package de.dailab.jiacpp.platform.tests;

import de.dailab.jiacpp.model.AgentContainerImage;
import de.dailab.jiacpp.util.RestHelper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class TestUtils {

    /**
     * Agent-container image providing some nonsensical actions useful for unit testing
     * This is the docker image of `examples/sample-container`. When adding a new feature to
     * the sample-container for testing some new function of the Runtime Platform, increment
     * the version number and push the image to the DAI Gitlab Docker Registry
     *
     * > docker build -t test-image examples/sample-container/
     * (change to TEST_IMAGE="test-image" and test locally if it works)
     * > docker tag test-image registry.gitlab.dai-labor.de/pub/unit-tests/jiacpp-sample-container:vXYZ
     * > docker push registry.gitlab.dai-labor.de/pub/unit-tests/jiacpp-sample-container:vXYZ
     */
    static final String TEST_IMAGE = "registry.gitlab.dai-labor.de/pub/unit-tests/jiacpp-sample-container:v10";

    /*
     * HELPER METHODS
     */

    public static AgentContainerImage getSampleContainerImage() {
        var image = new AgentContainerImage();
        image.setImageName(TEST_IMAGE);
        image.setExtraPorts(Map.of(8888, new AgentContainerImage.PortDescription()));
        return image;
    }

    public static String buildQuery(Map<String, Object> params) {
        if (params != null) {
            var query = new StringBuilder();
            for (String key : params.keySet()) {
                query.append(String.format("&%s=%s", key, params.get(key)));
            }
            return query.toString().replaceFirst("&", "?");
        } else {
            return "";
        }
    }

    public static HttpURLConnection request(String host, String method, String path, Object payload) throws IOException {
        return requestWithToken(host, method, path, payload, null);
    }

    // this is NOT using RestHelper since we are also interested in the exact HTTP Return Code
    public static HttpURLConnection requestWithToken(String host, String method, String path, Object payload, String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(host + path).openConnection();
        connection.setRequestMethod(method);

        if (token != null) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }

        if (payload != null) {
            String json = RestHelper.mapper.writeValueAsString(payload);
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
        return connection;
    }

    public static String result(HttpURLConnection connection) throws IOException {
        return new String(connection.getInputStream().readAllBytes());
    }

    public static <T> T result(HttpURLConnection connection, Class<T> type) throws IOException {
        return RestHelper.mapper.readValue(connection.getInputStream(), type);
    }

}
