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

package org.devtoolsgroup.hitlmcp.tools;

import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnreviewedToolsCheckTest {

    private final ListableBeanFactory beanFactory = mock(ListableBeanFactory.class);
    private final MockEnvironment environment = new MockEnvironment();
    private final UnreviewedToolsCheck check = new UnreviewedToolsCheck(beanFactory, environment);

    private static ToolCallback plainTool(String name) {
        return withDefinition(mock(ToolCallback.class), name);
    }

    private static <T extends ToolCallback> T withDefinition(T tool, String name) {
        when(tool.getToolDefinition()).thenReturn(
                ToolDefinition.builder().name(name).description(name).inputSchema("{}").build());
        return tool;
    }

    private static ToolCallback reviewedTool(String name) {
        return new InterceptingToolCallback(plainTool(name), null, null, null);
    }

    private void toolBeans(Map<String, ToolCallback> beans) {
        when(beanFactory.getBeansOfType(ToolCallback.class)).thenReturn(beans);
    }

    private void providerBeans(Map<String, ToolCallbackProvider> beans) {
        when(beanFactory.getBeansOfType(ToolCallbackProvider.class)).thenReturn(beans);
    }

    private void publishedTools(String... names) {
        McpSyncServer server = mock(McpSyncServer.class);
        when(server.listTools()).thenReturn(Arrays.stream(names)
                .map(name -> McpSchema.Tool.builder().name(name).inputSchema(Map.of("type", "object")).build())
                .toList());
        when(beanFactory.getBeansOfType(McpSyncServer.class)).thenReturn(Map.of("mcpSyncServer", server));
    }

    @Test
    void passesWhenAllToolsAreReviewed() {
        providerBeans(Map.of("interceptedTools", ToolCallbackProvider.from(List.of(reviewedTool("a")))));
        toolBeans(Map.of("single", reviewedTool("b")));
        publishedTools("a", "b");

        assertThatCode(check::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    void passesWithNoToolsAtAll() {
        assertThatCode(check::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    void failsOnUnreviewedToolCallbackBean() {
        toolBeans(Map.of("rawTool", plainTool("raw")));

        assertThatThrownBy(check::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ToolCallback bean 'rawTool' (tool 'raw')")
                .hasMessageContaining("@HitlMcpTools");
    }

    @Test
    void failsOnProviderWithUnreviewedTool() {
        providerBeans(Map.of("mixed", ToolCallbackProvider.from(List.of(reviewedTool("ok"), plainTool("raw")))));

        assertThatThrownBy(check::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ToolCallbackProvider bean 'mixed' (tool 'raw')")
                .hasMessageNotContaining("tool 'ok'");
    }

    @Test
    void failsOnToolPublishedFromAnotherSource() {
        providerBeans(Map.of("interceptedTools", ToolCallbackProvider.from(List.of(reviewedTool("a")))));
        publishedTools("a", "sneaky");

        assertThatThrownBy(check::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tools published by the MCP server from another source: [sneaky]");
    }

    @Test
    void checksStatelessServersToo() {
        McpStatelessSyncServer server = mock(McpStatelessSyncServer.class);
        when(server.listTools()).thenReturn(List.of(
                McpSchema.Tool.builder().name("sneaky").inputSchema(Map.of("type", "object")).build()));
        when(beanFactory.getBeansOfType(McpStatelessSyncServer.class)).thenReturn(Map.of("server", server));

        assertThatThrownBy(check::afterSingletonsInstantiated).hasMessageContaining("[sneaky]");
    }

    @Test
    void ignoresMcpClientToolsByDefault() {
        toolBeans(Map.of("clientTool", withDefinition(mock(SyncMcpToolCallback.class), "remote")));
        SyncMcpToolCallbackProvider clientProvider = mock(SyncMcpToolCallbackProvider.class);
        ToolCallback remoteTool = plainTool("remote2");
        when(clientProvider.getToolCallbacks()).thenReturn(new ToolCallback[] {remoteTool});
        providerBeans(Map.of("clientTools", clientProvider));

        assertThatCode(check::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    void failsOnMcpClientToolsWhenTheyArePublished() {
        environment.setProperty("spring.ai.mcp.server.expose-mcp-client-tools", "true");
        toolBeans(Map.of("clientTool", withDefinition(mock(SyncMcpToolCallback.class), "remote")));
        SyncMcpToolCallbackProvider clientProvider = mock(SyncMcpToolCallbackProvider.class);
        ToolCallback remoteTool = plainTool("remote2");
        when(clientProvider.getToolCallbacks()).thenReturn(new ToolCallback[] {remoteTool});
        providerBeans(Map.of("clientTools", clientProvider));

        assertThatThrownBy(check::afterSingletonsInstantiated)
                .hasMessageContaining("ToolCallback bean 'clientTool' (tool 'remote')")
                .hasMessageContaining("ToolCallbackProvider bean 'clientTools' (tool 'remote2')");
    }
}
