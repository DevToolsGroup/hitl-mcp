package org.devtoolsgroup.hitlmcp.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Example of a Spring Boot application using the library:
 * the {@code @HitlMcpTools} classes of this package are found by component scan.
 * Run with {@code mvn spring-boot:test-run}.
 */
@SpringBootApplication
public class ExampleSpringBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExampleSpringBootApplication.class, args);
    }
}
