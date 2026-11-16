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

package org.devtoolsgroup.hitlmcp.web;

import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerSseProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.web.context.WebApplicationContext;

import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Prints the MCP endpoint and the review page URLs to the console once the web server accepts connections.
 * Works the same for Spring Boot applications and for {@code HitlMcpServer.builder()},
 * and shows the real port, also when it was picked at random with {@code server.port=0}.
 * Uses {@code System.out}, not a logger, so the URLs are visible whatever logging configuration the application has.
 */
public class StartupUrlsPrinter implements ApplicationListener<WebServerInitializedEvent> {

    static final String UI_PATH = "/mcp-review/";

    private final ObjectProvider<ServerProperties> serverProperties;
    private final ObjectProvider<McpServerProperties> mcpProperties;
    private final ObjectProvider<McpServerStreamableHttpProperties> streamableHttpProperties;
    private final ObjectProvider<McpServerSseProperties> sseProperties;

    public StartupUrlsPrinter(ObjectProvider<ServerProperties> serverProperties,
                              ObjectProvider<McpServerProperties> mcpProperties,
                              ObjectProvider<McpServerStreamableHttpProperties> streamableHttpProperties,
                              ObjectProvider<McpServerSseProperties> sseProperties) {
        this.serverProperties = serverProperties;
        this.mcpProperties = mcpProperties;
        this.streamableHttpProperties = streamableHttpProperties;
        this.sseProperties = sseProperties;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if (event.getApplicationContext().getServerNamespace() != null) {
            return; // a separate server, for example the actuator's management port
        }
        String base = baseUrl(event.getWebServer().getPort(), contextPath(event));
        System.out.println();
        System.out.println("HITL MCP server is running:");
        System.out.println("  MCP endpoint, to register with an AI agent: " + base + mcpEndpoint());
        System.out.println("  Review page, to open in a browser:          " + base + UI_PATH);
        System.out.println();
    }

    private String baseUrl(int port, String contextPath) {
        ServerProperties server = serverProperties.getIfAvailable();
        String scheme = server != null && Ssl.isEnabled(server.getSsl()) ? "https" : "http";
        return scheme + "://" + host(server == null ? null : server.getAddress()) + ":" + port + contextPath;
    }

    /** The address the server is bound to, or {@code localhost} when it listens on all addresses. */
    private static String host(InetAddress address) {
        if (address == null || address.isAnyLocalAddress()) {
            return "localhost";
        }
        if (address instanceof Inet6Address) {
            return address.isLoopbackAddress() ? "[::1]" : "[" + address.getHostAddress() + "]";
        }
        return address.getHostAddress();
    }

    private static String contextPath(WebServerInitializedEvent event) {
        if (event.getApplicationContext() instanceof WebApplicationContext web && web.getServletContext() != null) {
            return web.getServletContext().getContextPath();
        }
        return "";
    }

    private String mcpEndpoint() {
        McpServerProperties mcp = mcpProperties.getIfAvailable();
        if (mcp != null && mcp.getProtocol() == McpServerProperties.ServerProtocol.SSE) {
            McpServerSseProperties sse = sseProperties.getIfAvailable();
            return sse != null ? sse.getSseEndpoint() : "/sse";
        }
        McpServerStreamableHttpProperties streamable = streamableHttpProperties.getIfAvailable();
        return streamable != null ? streamable.getMcpEndpoint() : "/mcp";
    }
}
