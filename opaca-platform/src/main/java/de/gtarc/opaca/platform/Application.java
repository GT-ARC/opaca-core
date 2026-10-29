package de.gtarc.opaca.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The actual Spring Boot application, starting the Controller
 */
@SpringBootApplication
public class Application {

    public static final String VERSION = "0.6-SNAPSHOT";

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
