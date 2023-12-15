package de.dailab.jiacpp.platform.tests;

import de.dailab.jiacpp.platform.Application;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;

import static de.dailab.jiacpp.platform.tests.TestUtils.*;

public class InterPlatformTests {

    private static final int PLATFORM_A_PORT = 8001;
    private static final int PLATFORM_B_PORT = 8002;

    private static final String PLATFORM_A = "http://localhost:" + PLATFORM_A_PORT;
    private static final String PLATFORM_B = "http://localhost:" + PLATFORM_B_PORT;

    private static ConfigurableApplicationContext platformA = null;
    private static ConfigurableApplicationContext platformB = null;

    private static String containerId = null;

    @BeforeClass
    public static void setupPlatforms() throws IOException {
        platformA = SpringApplication.run(Application.class,
                "--server.port=" + PLATFORM_A_PORT);
        platformB = SpringApplication.run(Application.class,
                "--server.port=" + PLATFORM_B_PORT);
        containerId = postSampleContainer(PLATFORM_A);
    }

    @AfterClass
    public static void stopPlatforms() {
        platformA.close();
        platformB.close();
    }

}
