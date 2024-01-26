package de.gtarc.opaca.platform.tests;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import de.gtarc.opaca.model.AgentContainerImage;
import de.gtarc.opaca.model.AgentContainerImage.ImageParameter;
import de.gtarc.opaca.model.LoginConnection;
import de.gtarc.opaca.model.PostAgentContainer;
import de.gtarc.opaca.model.RuntimePlatform;
import de.gtarc.opaca.util.RestHelper;
import org.apache.commons.lang3.SystemUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    private static DockerClient dockerClient = null;
    private static String mongoContId = null;

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
    public static int streamRequest(String baseUrl, String method, String path, byte[] payload) throws IOException {
        // TODO reduce code duplication a bit?
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
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

    // Starts a local mongo db on port 27017 for testing purposes
    public static void startMongoDB() throws InterruptedException {
        DockerClientConfig dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(getLocalDockerHost())
                .build();

        DockerHttpClient dockerHttpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(dockerConfig.getDockerHost())
                .sslConfig(dockerConfig.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        dockerClient = DockerClientImpl.getInstance(dockerConfig, dockerHttpClient);

        // Use default port 27017, but external port 27018 for testing
        ExposedPort mongoPort = ExposedPort.tcp(27017);
        Ports portBindings = new Ports();
        portBindings.bind(mongoPort, Ports.Binding.bindPort(27018));

        // Use volumes to store the temporary data
        Volume dataVolume = new Volume("/data/db");
        Volume configVolume = new Volume("/data/configdb");

        // Check if mongo:7.0.4 image is locally available, if not -> pull from remote
        List<String> repoTags = dockerClient.listImagesCmd().exec().stream()
                .flatMap(image -> Arrays.stream(image.getRepoTags())).collect(Collectors.toList());
        if (!repoTags.contains("mongo:7.0.4")) {
            dockerClient.pullImageCmd("mongo:7.0.4").exec(new PullImageResultCallback()).awaitCompletion();
        }

        CreateContainerResponse res = dockerClient.createContainerCmd("mongo:7.0.4")
                .withName("opaca-data-test")
                .withVolumes(dataVolume, configVolume)
                .withHostConfig(HostConfig.newHostConfig().withPortBindings(portBindings).withBinds(
                        new Bind("opaca-platform_mongodb_data_test", dataVolume),
                        new Bind("opaca-platform_mongodb_config_test", configVolume)))
                .withExposedPorts(mongoPort)
                .withEnv("MONGO_INITDB_ROOT_USERNAME=user", "MONGO_INITDB_ROOT_PASSWORD=pass")
                .exec();

        mongoContId = res.getId();

        dockerClient.startContainerCmd(res.getId()).exec();

        if (!checkContainerRunning()) throw new RuntimeException("Failed to create MongoDB Container!");
    }

    public static void stopMongoDB() {
        dockerClient.stopContainerCmd(mongoContId).exec();
        dockerClient.removeContainerCmd(mongoContId).exec();

        // Remove the temporary data volumes
        dockerClient.removeVolumeCmd("opaca-platform_mongodb_data_test").exec();
        dockerClient.removeVolumeCmd("opaca-platform_mongodb_config_test").exec();
    }

    private static boolean checkContainerRunning() {
        if (mongoContId == null) return false;
        var start = System.currentTimeMillis();

        // Wait 5 seconds to see if container has started
        while(System.currentTimeMillis() < start + 5000) {
            InspectContainerResponse containerResponse = dockerClient.inspectContainerCmd(mongoContId).exec();
            String response = containerResponse.getState().getStatus();
            if ("running".equals(response)) {
                return true;
            }
            else if ("exited".equals(response) || "dead".equals(response) ||
                     "paused".equals(response) || "restarting".equals(response)) {
                return false;
            }
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                break;
            }
        }
        return false;
    }


    private static String getLocalDockerHost() {
        return SystemUtils.IS_OS_WINDOWS
                ? "npipe:////./pipe/docker_engine"
                : "unix:///var/run/docker.sock";
    }

}
