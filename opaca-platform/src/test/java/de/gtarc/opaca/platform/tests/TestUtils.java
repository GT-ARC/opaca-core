package de.gtarc.opaca.platform.tests;

import de.gtarc.opaca.model.*;
import de.gtarc.opaca.model.AgentContainerImage.ImageParameter;
import de.gtarc.opaca.platform.Application;
import de.gtarc.opaca.util.RestHelper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.*;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Class providing util methods and constants used by the other Test classes.
 */
public class TestUtils {

    /**
     * Agent-container image providing some nonsensical actions useful for unit testing
     * This is the docker image of `examples/sample-container`. The image is build automatically
     * during CI. When running tests locally, make sure to build the image first, with this name.
     */
    static final String TEST_IMAGE = "sample-agent-container-image";

    /**
     * Start OPACA Runtime for testing using specific settings.
     *
     * @param port The port there the platform should run.
     * @param defaultImages Whether to use the default-test-images directory.
     * @param requireAuth Whether to require authentication (will also require Keycloak)
     * @param useKeycloak Whether to use Keycloak (does not require auth)
     * @return application context, to be closed when tests are done.
     */
    public static ConfigurableApplicationContext startPlatform(int port, boolean defaultImages, boolean requireAuth, boolean useKeycloak) {
        List<String> parameters = new ArrayList<>();
        parameters.add("--server.port=" + port);
        if (defaultImages) {
            parameters.add("--opaca.default_image_directory=./default-test-images");
        }
        if (requireAuth) {
            parameters.add("--opaca.security.requireAuth=true");
        }
        if (useKeycloak || requireAuth) {
            var keycloak = "http://" + getOwnIP() + ":9000/realms/opaca";
            parameters.add("--opaca.security.kc_issuer_uri=" + keycloak);
            parameters.add("--opaca.security.kc_clientid=opaca-rp");
            parameters.add("--opaca.security.kc_admin=admin");
            parameters.add("--opaca.security.kc_admin_pw=admin");
            parameters.add("--spring.security.oauth2.resourceserver.jwt.issuer-uri=" + keycloak);
        }
        return SpringApplication.run(Application.class, parameters.toArray(String[]::new));
    }

    public static String getOwnIP() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            return socket.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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

    public static HttpURLConnection request(String host, String method, String path, Object payload) throws Exception {
        return requestWithToken(host, method, path, payload, null);
    }

    // this is NOT using RestHelper since we are also interested in the exact HTTP Return Code
    public static int streamRequest(String baseUrl, String method, String path, byte[] payload) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URI(baseUrl + path).toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

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

        return connection.getResponseCode();
    }

    // this is NOT using RestHelper since we are also interested in the exact HTTP Return Code
    public static HttpURLConnection requestWithToken(String host, String method, String path, Object payload, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URI(host + path).toURL().openConnection();
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

    public static ErrorResponse error(HttpURLConnection connection) throws IOException {
        // IMPORTANT: FOR SOME REASON, THE ERROR STREAM IS NULL BEFORE THE STATUS CODE HAS BEEN RETRIEVED1!
        var content = new String(connection.getErrorStream().readAllBytes());
        return RestHelper.readObject(content, ErrorResponse.class);
    }

    public static String getBaseUrl(String localUrl) throws Exception {
        var con = request(localUrl, "GET", "/info", null);
        return result(con, RuntimePlatform.class).getBaseUrl();
    }

    public static String postSampleContainer(String platformUrl) throws Exception {
        var postContainer = getSampleContainerImage();
        var con = request(platformUrl, "POST", "/containers", postContainer);
        if (con.getResponseCode() != 200) {
            var message = new String(con.getErrorStream().readAllBytes());
            throw new IOException("Failed to POST sample container: " + message);
        }
        return result(con);
    }

    public static void connectPlatforms(String platformUrl, String connectedUrl) throws Exception {
        var connectedBaseUrl = getBaseUrl(connectedUrl);
        var loginCon = new ConnectionRequest(connectedBaseUrl, true, null);
        var con = request(platformUrl, "POST", "/connections", loginCon);
        if (con.getResponseCode() != 200) {
            var message = new String(con.getErrorStream().readAllBytes());
            throw new IOException("Failed to connect platforms: " + message);
        }
    }
}
