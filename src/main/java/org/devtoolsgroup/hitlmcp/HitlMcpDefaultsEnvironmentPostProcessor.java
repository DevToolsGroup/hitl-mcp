package org.devtoolsgroup.hitlmcp;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Adds the library's defaults from {@value #DEFAULTS} with the lowest precedence,
 * so the application's own {@code application.properties}, environment variables etc. override any of them.
 * A library cannot ship {@code application.properties}: the application's file of the same name would hide it.
 * Registered in {@code META-INF/spring.factories}.
 */
public class HitlMcpDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String DEFAULTS = "hitlmcp-defaults.properties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            environment.getPropertySources().addLast(new ResourcePropertySource("hitlMcpDefaults",
                    new ClassPathResource(DEFAULTS, HitlMcpDefaultsEnvironmentPostProcessor.class.getClassLoader())));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + DEFAULTS, e);
        }
    }
}
