/*
MIT License

Copyright (c) 2026-present DevToolsGroup (https://github.com/DevToolsGroup)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package org.devtoolsgroup.hitlmcp;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point for applications that do not use Spring Boot themselves.
 * <pre>{@code
 * HitlMcpServer server = HitlMcpServer.builder()
 *         .tools(new MyTools())
 *         .port(8080)
 *         .start(args);
 * }</pre>
 * The server runs in the background until {@link #close()} is called or the JVM exits.
 * Spring Boot applications do not need this class: they annotate their tool beans with {@link HitlMcpTools}.
 */
public final class HitlMcpServer implements AutoCloseable {

    private final ConfigurableApplicationContext context;

    private HitlMcpServer(ConfigurableApplicationContext context) {
        this.context = context;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The actual HTTP port, useful when started with port {@code 0}. */
    public int getPort() {
        return context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
    }

    /** Stops the server. Calls still waiting for the user end with the {@code CANCELLED} status. */
    @Override
    public void close() {
        context.close();
    }

    public static final class Builder {

        private final List<Object> tools = new ArrayList<>();
        private final Map<String, Object> properties = new LinkedHashMap<>();

        private Builder() {
        }

        /** Adds tool objects. Their classes must be annotated with {@link HitlMcpTools}. */
        public Builder tools(Object... toolObjects) {
            for (Object tool : toolObjects) {
                if (!AnnotatedElementUtils.hasAnnotation(tool.getClass(), HitlMcpTools.class)) {
                    throw new IllegalArgumentException(
                            tool.getClass().getName() + " is not annotated with @" + HitlMcpTools.class.getSimpleName());
                }
                tools.add(tool);
            }
            return this;
        }

        /** HTTP port, {@code server.port}. Default 8080, {@code 0} picks a free port. */
        public Builder port(int port) {
            return property("server.port", port);
        }

        /** History file, {@code hitlmcp.history.file}. */
        public Builder historyFile(Path file) {
            return property("hitlmcp.history.file", file.toString());
        }

        /** History file, {@code hitlmcp.history.file}. */
        public Builder historyFile(String file) {
            return property("hitlmcp.history.file", file);
        }

        /**
         * Sets any Spring Boot or library property, for example {@code hitlmcp.ui.page-size}.
         * Properties read before the context starts, such as {@code logging.*} and {@code spring.main.*}, have no effect.
         */
        public Builder property(String key, Object value) {
            properties.put(key, value);
            return this;
        }

        /** Starts the server and returns once it accepts connections. */
        public HitlMcpServer start(String... args) {
            List<Object> toolsToRegister = List.copyOf(tools);
            Map<String, Object> propertiesToApply = Map.copyOf(properties);
            ConfigurableApplicationContext context = new SpringApplicationBuilder(Application.class)
                    .bannerMode(Banner.Mode.OFF)
                    .initializers(ctx -> {
                        ctx.getEnvironment().getPropertySources()
                                .addFirst(new MapPropertySource("hitlMcpServerBuilder", propertiesToApply));
                        for (int i = 0; i < toolsToRegister.size(); i++) {
                            Object tool = toolsToRegister.get(i);
                            ctx.getBeanFactory().registerSingleton(tool.getClass().getName() + "#" + i, tool);
                        }
                    })
                    .run(args);
            return new HitlMcpServer(context);
        }
    }

    /** Minimal Spring Boot application: everything comes from {@code HitlMcpAutoConfiguration}. */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Application {
    }
}
