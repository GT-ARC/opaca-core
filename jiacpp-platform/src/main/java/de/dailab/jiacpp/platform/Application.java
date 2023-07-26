package de.dailab.jiacpp.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * The actual Spring Boot application, starting the Controller
 */
@SpringBootApplication
@ComponentScan("de.dailab.jiacpp")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
