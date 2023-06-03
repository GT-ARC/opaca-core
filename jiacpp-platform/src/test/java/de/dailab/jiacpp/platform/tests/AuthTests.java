package de.dailab.jiacpp.platform.tests;

import de.dailab.jiacpp.model.*;
import de.dailab.jiacpp.platform.Application;
import de.dailab.jiacpp.util.RestHelper;

import org.junit.runners.MethodSorters;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AuthTests {

    private static final String PLATFORM_PORT = "8003";
    private final String PLATFORM = "http://localhost:" + PLATFORM_PORT;
    private final String TEST_IMAGE = "ping-container-image";

    private static ConfigurableApplicationContext platform = null;
    private static String token = null;
    private static String containerId = null;

    @BeforeClass
    public static void setupPlatform() {
        platform = SpringApplication.run(Application.class, "--server.port=" + PLATFORM_PORT,
                "--default_image_directory=./default-test-images", "--security.enableJwt=true");
    }


    @Test
    public void test1Login() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("username", "myUsername");
        params.put("password", "myPassword");
        var con = request(PLATFORM, "POST", "/login", params);
        Assert.assertEquals(200, con.getResponseCode());
        token = result(con);
        Assert.assertNotNull(token);
    }
    

    @Test
    public void test2WithToken() throws Exception {
        var con = requestWithToken(PLATFORM, "GET", "/info", null, token);
        int responseCode = con.getResponseCode();
        Assert.assertEquals(200, responseCode);
        containerId = result(con);
    }
    
    @Test
    public void test2WithoutToken() throws Exception {
        var con = requestWithoutToken(PLATFORM, "GET", "/info", null);
        Assert.assertEquals(403, con.getResponseCode());
    }
    


    public AgentContainerImage getSampleContainerImage() {
        var image = new AgentContainerImage();
        image.setImageName(TEST_IMAGE);
        image.setExtraPorts(Map.of(8888, new AgentContainerImage.PortDescription()));
        return image;
    }


    public HttpURLConnection request(String host, String method, String path, Map<String, String> parameters) throws IOException {
        StringBuilder paramBuilder = new StringBuilder();
        for(Map.Entry<String,String> parameter : parameters.entrySet()){
            if(paramBuilder.length() != 0)
                paramBuilder.append('&');
            paramBuilder.append(URLEncoder.encode(parameter.getKey(), "UTF-8"));
            paramBuilder.append('=');
            paramBuilder.append(URLEncoder.encode(parameter.getValue(), "UTF-8"));
        }
    
        URL url = new URL(host + path + "?" + paramBuilder.toString());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.connect();
        
        return connection;
    }
    


    public HttpURLConnection requestWithToken(String host, String method, String path, Object payload, String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(host + path).openConnection();
        connection.setRequestMethod(method);
    
        // set Authorization header with Bearer token
        connection.setRequestProperty("Authorization", "Bearer " + token);
    
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

    public HttpURLConnection requestWithoutToken(String host, String method, String path, Object payload) throws IOException {
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
    
    
    public String result(HttpURLConnection connection) throws IOException {
        return new String(connection.getInputStream().readAllBytes());
    }
    

    public <T> T result(HttpURLConnection connection, Class<T> type) throws IOException {
        return RestHelper.mapper.readValue(connection.getInputStream(), type);
    }
}
