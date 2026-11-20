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

import org.devtoolsgroup.hitlmcp.HitlMcpServer;
import org.devtoolsgroup.hitlmcp.example.TextTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sends big decisions to the review WebSocket of a real server, because the size limit is enforced by the
 * servlet container (Tomcat), not by the handler. Tomcat's own default limit is 8192 characters.
 */
class ReviewWebSocketMessageSizeTest {

    /** Long enough for Tomcat to close the connection, if it would. */
    private static final long CLOSE_WAIT_MS = 2000;

    @Test
    void acceptsDecisionsAboveTomcatsDefaultLimit(@TempDir Path tempDir) throws Exception {
        try (HitlMcpServer server = start(tempDir, null)) {
            Client client = Client.connect(server.getPort());

            client.session.sendMessage(new TextMessage(decision(20_000)));

            assertThatThrownBy(() -> client.closed.get(CLOSE_WAIT_MS, TimeUnit.MILLISECONDS))
                    .as("the connection must stay open").isInstanceOf(TimeoutException.class);
            client.session.close();
        }
    }

    @Test
    void closesTheConnectionAboveTheConfiguredLimit(@TempDir Path tempDir) throws Exception {
        try (HitlMcpServer server = start(tempDir, 1000)) {
            Client client = Client.connect(server.getPort());
            assertThat(client.snapshot.get(CLOSE_WAIT_MS, TimeUnit.MILLISECONDS)).contains("\"maxMessageChars\":1000");

            client.session.sendMessage(new TextMessage(decision(2000)));

            assertThat(client.closed.get(CLOSE_WAIT_MS, TimeUnit.MILLISECONDS).getCode())
                    .isEqualTo(CloseStatus.TOO_BIG_TO_PROCESS.getCode());
        }
    }

    private static HitlMcpServer start(Path tempDir, Integer maxMessageChars) {
        HitlMcpServer.Builder builder = HitlMcpServer.builder()
                .tools(new TextTools())
                .port(0)
                .historyFile(tempDir.resolve("history.jsonl"));
        if (maxMessageChars != null) {
            builder.property("hitlmcp.ui.max-message-chars", maxMessageChars);
        }
        return builder.start();
    }

    /** A decision for a review that does not exist, which the server ignores, of at least the given length. */
    private static String decision(int length) {
        return """
                {"type":"decision","id":"none","phase":"AWAITING_RESULT_APPROVAL","approved":true,\
                "updatedResult":"%s"}""".formatted("x".repeat(length));
    }

    private static final class Client extends TextWebSocketHandler {

        final CompletableFuture<String> snapshot = new CompletableFuture<>();
        final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
        WebSocketSession session;

        static Client connect(int port) throws Exception {
            Client client = new Client();
            client.session = new StandardWebSocketClient()
                    .execute(client, "ws://127.0.0.1:" + port + "/mcp-review/ws")
                    .get(10, TimeUnit.SECONDS);
            return client;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            snapshot.complete(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closed.complete(status);
        }
    }
}
