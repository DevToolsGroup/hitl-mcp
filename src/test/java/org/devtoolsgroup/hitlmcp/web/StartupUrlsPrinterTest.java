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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerSseProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class StartupUrlsPrinterTest {

    private static final String NL = System.lineSeparator();

    private final ServerProperties server = new ServerProperties();
    private McpServerProperties mcp;
    private McpServerStreamableHttpProperties streamable;
    private McpServerSseProperties sse;

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private void print(int port, String serverNamespace) {
        WebServerApplicationContext context = mock(WebServerApplicationContext.class);
        when(context.getServerNamespace()).thenReturn(serverNamespace);
        WebServer webServer = mock(WebServer.class);
        when(webServer.getPort()).thenReturn(port);
        WebServerInitializedEvent event = mock(WebServerInitializedEvent.class);
        when(event.getApplicationContext()).thenReturn(context);
        when(event.getWebServer()).thenReturn(webServer);

        new StartupUrlsPrinter(provider(server), provider(mcp), provider(streamable), provider(sse))
                .onApplicationEvent(event);
    }

    @Test
    void printsDefaultUrls(CapturedOutput output) {
        print(8080, null);

        assertThat(output).contains("http://localhost:8080/mcp" + NL, "http://localhost:8080/mcp-review/" + NL);
    }

    @Test
    void usesBoundAddressAndRealPort(CapturedOutput output) throws Exception {
        server.setAddress(InetAddress.getByName("127.0.0.1"));

        print(54321, null);

        assertThat(output).contains("http://127.0.0.1:54321/mcp" + NL, "http://127.0.0.1:54321/mcp-review/" + NL);
    }

    @Test
    void usesLocalhostWhenListeningOnAllAddresses(CapturedOutput output) throws Exception {
        server.setAddress(InetAddress.getByName("0.0.0.0"));

        print(8080, null);

        assertThat(output).contains("http://localhost:8080/mcp" + NL);
    }

    @Test
    void bracketsIpv6Addresses(CapturedOutput output) throws Exception {
        server.setAddress(InetAddress.getByName("::1"));

        print(8080, null);

        assertThat(output).contains("http://[::1]:8080/mcp" + NL);
    }

    @Test
    void usesHttpsWhenSslIsEnabled(CapturedOutput output) {
        Ssl ssl = new Ssl();
        ssl.setEnabled(true);
        server.setSsl(ssl);

        print(8443, null);

        assertThat(output).contains("https://localhost:8443/mcp" + NL);
    }

    @Test
    void usesConfiguredStreamableEndpoint(CapturedOutput output) {
        streamable = new McpServerStreamableHttpProperties();
        streamable.setMcpEndpoint("/custom-mcp");

        print(8080, null);

        assertThat(output).contains("http://localhost:8080/custom-mcp" + NL);
    }

    @Test
    void usesSseEndpointForSseProtocol(CapturedOutput output) {
        mcp = new McpServerProperties();
        mcp.setProtocol(McpServerProperties.ServerProtocol.SSE);
        sse = new McpServerSseProperties();
        sse.setSseEndpoint("/events");

        print(8080, null);

        assertThat(output).contains("http://localhost:8080/events" + NL);
    }

    @Test
    void ignoresOtherServers(CapturedOutput output) {
        print(9090, "management");

        assertThat(output).doesNotContain("HITL MCP server is running");
    }
}
