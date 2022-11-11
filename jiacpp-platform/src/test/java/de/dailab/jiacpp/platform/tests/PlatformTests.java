package de.dailab.jiacpp.platform.tests;

import com.sun.jdi.connect.spi.Connection;
import de.dailab.jiacpp.model.AgentContainerImage;
import de.dailab.jiacpp.model.RuntimePlatform;
import de.dailab.jiacpp.util.RestHelper;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Before running these tests, make sure to start the runtime platform with
 * `docker-compose up` (after building everything with `mvn install`).
 * Some of the tests depend on each others, so best always execute all tests.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PlatformTests {

    private String PLATFORM_A = "http://localhost:8001";
    private String PLATFORM_B = "http://localhost:8002";

    private static String containerId = null;

    /*
     * TEST THAT STUFF WORKS
     */

    /**
     * call info, make sure platform is up
     */
    @Test
    public void test10Platform() throws Exception {
        var con = request(PLATFORM_A, "GET", "/info", null);
        Assert.assertEquals(200, con.getResponseCode());
        var info = result(con, RuntimePlatform.class);
        Assert.assertNotNull(info);
        System.out.println(info);
    }

    /**
     * deploy sample container
     */
    @Test
    public void test20Deploy() throws Exception {
        var container = new AgentContainerImage();
        container.setImageName("sample-agent-container-image");
        var con = request(PLATFORM_A, "POST", "/containers", container);
        Assert.assertEquals(200, con.getResponseCode());
        containerId = new String(con.getInputStream().readAllBytes());
        System.out.println(containerId);

        Thread.sleep(2000);

        con = request(PLATFORM_A, "GET", "/containers", null);
        var lst = result(con, List.class);
        Assert.assertEquals(1, lst.size());
        System.out.println(lst.size());
    }

    /**
     * get container info
     */
    @Test
    public void test30GetInfo() throws Exception {

        System.out.println(containerId);

        var con = request(PLATFORM_A, "GET", "/containers", null);
        Assert.assertEquals(200, con.getResponseCode());
        List res = result(con, List.class);
        System.out.println(res);
        System.out.println(res.size());
        System.out.println(res.get(0).getClass());


    }




    // call invoke, check result
    // call invoke with agent, check result
    // call send, check that it arrived via another invoke
    // call broadcast, check that it arrived via another invoke
    // connect to second platform, check that both are connected
    // repeat above tests, but with redirect to second platform
    // disconnect platforms, check that both are disconnected

    /**
     * undeploy container, check that it's gone
     */
    @Test
    public void test90Undeploy() throws Exception {

        System.out.println(containerId);

        var con = request(PLATFORM_A, "DELETE", "/containers/" + containerId, null);
        Assert.assertEquals(200, con.getResponseCode());
        var res = result(con, Boolean.class);
        System.out.println(res);
        Assert.assertTrue(res);

        Thread.sleep(1000);

        con = request(PLATFORM_A, "GET", "/containers", null);
        var lst = result(con, List.class);
        Assert.assertEquals(0, lst.size());
        System.out.println(lst.size());
    }


    /*
     * TEST HOW STUFF FAILS
     */

    // try to invoke unknown action
    // try to send message to unknown agent
    // try to call route with mismatched payload format?
    // try to deploy unknown container
    // try to undeploy unknown container
    // try to connect unknown platform
    // try to disconnect unknown platform

    /*
     * HELPER METHODS
     */

    public HttpURLConnection request(String host, String method, String path, Object payload) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(host + path).openConnection();
        connection.setRequestMethod(method);

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

    public <T> T result(HttpURLConnection connection, Class<T> type) throws IOException {
        return RestHelper.mapper.readValue(connection.getInputStream(), type);
    }

}
