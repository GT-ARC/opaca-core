package de.gtarc.opaca.platform.tests;

import de.gtarc.opaca.model.AgentContainerImage;
import de.gtarc.opaca.model.AgentContainerImage.ImageParameter;
import de.gtarc.opaca.model.LoginConnection;
import de.gtarc.opaca.model.PostAgentContainer;
import de.gtarc.opaca.model.RuntimePlatform;
import de.gtarc.opaca.util.RestHelper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
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
     * > docker tag test-image registry.gitlab.dai-labor.de/pub/unit-tests/opaca-sample-container:vXYZ
     * > docker push registry.gitlab.dai-labor.de/pub/unit-tests/opaca-sample-container:vXYZ
     */
    static final String TEST_IMAGE = "registry.gitlab.dai-labor.de/pub/unit-tests/opaca-sample-container:v17";

    /*
     * HELPER METHODS
     */

    public static PostAgentContainer getSampleContainerImage() {
        var image = new AgentContainerImage();
        image.setImageName(TEST_IMAGE);
        image.setExtraPorts(Map.of(
                8888, new AgentContainerImage.PortDescription("TCP", "TCP Test Port"),
                8889, new AgentContainerImage.PortDescription("UDP", "UDP Test Port")
        ));
        return new PostAgentContainer(image, Map.of(), null);
    }

    public static void addImageParameters(PostAgentContainer sampleRequest) {
        // parameters should match those defined in the sample-agent-container-image's own container.json!
        sampleRequest.getImage().setParameters(List.of(
                new ImageParameter("database", "string", false, false, "mongodb"),
                new ImageParameter("username", "string", true, false, null),
                new ImageParameter("password", "string", true, true, null)
        ));
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

    public static String error(HttpURLConnection connection) throws IOException {
        return new String(connection.getErrorStream().readAllBytes());
    }

    public static String getBaseUrl(String localUrl) throws IOException {
        var con = request(localUrl, "GET", "/info", null);
        return result(con, RuntimePlatform.class).getBaseUrl();
    }

    public static String postSampleContainer(String platformUrl) throws IOException {
        var postContainer = getSampleContainerImage();
        var con = request(platformUrl, "POST", "/containers", postContainer);
        if (con.getResponseCode() != 200) {
            var message = new String(con.getErrorStream().readAllBytes());
            throw new IOException("Failed to POST sample container: " + message);
        }
        return result(con);
    }

    public static void connectPlatforms(String platformUrl, String connectedUrl) throws IOException {
        var connectedBaseUrl = getBaseUrl(connectedUrl);
        var loginCon = new LoginConnection(null, null, connectedBaseUrl);
        var con = request(platformUrl, "POST", "/connections", loginCon);
        if (con.getResponseCode() != 200) {
            var message = new String(con.getErrorStream().readAllBytes());
            throw new IOException("Failed to connect platforms: " + message);
        }
    }

}
