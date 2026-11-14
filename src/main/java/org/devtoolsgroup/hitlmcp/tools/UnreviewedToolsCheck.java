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

import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpStatelessAsyncServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.AsyncMcpToolCallback;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Refuses to start the application if a tool would be published without the user's review.
 * <p>
 * Spring AI publishes every {@link ToolCallback} and {@link ToolCallbackProvider} bean, not only the reviewed ones
 * from {@link ToolsConfig}. Tools can also reach the MCP server in other ways,
 * for example as {@code SyncToolSpecification} beans or {@code @McpTool} methods with the annotation scanner enabled.
 * So there are two checks:
 * <ol>
 *     <li>every {@link ToolCallback} and {@link ToolCallbackProvider} bean holds reviewed tools only,
 *     naming the offending beans;</li>
 *     <li>every tool the MCP server actually publishes is a reviewed one, catching all the other ways.</li>
 * </ol>
 * Runs after all singletons are created, so a failure stops the application before the web server accepts requests.
 */
@RequiredArgsConstructor
public class UnreviewedToolsCheck implements SmartInitializingSingleton {

    private static final String EXPOSE_MCP_CLIENT_TOOLS = "spring.ai.mcp.server.expose-mcp-client-tools";

    private final ListableBeanFactory beanFactory;
    private final Environment environment;

    @Override
    public void afterSingletonsInstantiated() {
        boolean exposeMcpClientTools = environment.getProperty(EXPOSE_MCP_CLIENT_TOOLS, Boolean.class, false);
        List<String> problems = new ArrayList<>();
        Set<String> reviewedTools = new TreeSet<>();

        beanFactory.getBeansOfType(ToolCallback.class).forEach((name, tool) -> {
            if (tool instanceof InterceptingToolCallback) {
                reviewedTools.add(tool.getToolDefinition().name());
            } else if (exposeMcpClientTools || !isMcpClientTool(tool)) {
                problems.add("ToolCallback bean '" + name + "' (tool '" + tool.getToolDefinition().name() + "')");
            }
        });

        beanFactory.getBeansOfType(ToolCallbackProvider.class).forEach((name, provider) -> {
            if (!exposeMcpClientTools && isMcpClientToolProvider(provider)) {
                return;
            }
            for (ToolCallback tool : provider.getToolCallbacks()) {
                if (tool instanceof InterceptingToolCallback) {
                    reviewedTools.add(tool.getToolDefinition().name());
                } else {
                    problems.add("ToolCallbackProvider bean '" + name + "' (tool '"
                            + tool.getToolDefinition().name() + "')");
                }
            }
        });

        Set<String> unreviewedPublished = new TreeSet<>(publishedTools());
        unreviewedPublished.removeAll(reviewedTools);
        if (problems.isEmpty() && !unreviewedPublished.isEmpty()) {
            problems.add("tools published by the MCP server from another source: " + unreviewedPublished);
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Tools that bypass the user's review were found:\n  - "
                    + String.join("\n  - ", problems)
                    + "\nPublish tools only as @Tool methods of classes annotated with @HitlMcpTools.");
        }
    }

    /** Names of the tools of whichever MCP server variant is configured. */
    private Set<String> publishedTools() {
        List<McpSchema.Tool> tools = new ArrayList<>();
        beans(McpSyncServer.class).forEach(server -> tools.addAll(server.listTools()));
        beans(McpStatelessSyncServer.class).forEach(server -> tools.addAll(server.listTools()));
        beans(McpAsyncServer.class).forEach(server -> tools.addAll(server.listTools().collectList().block()));
        beans(McpStatelessAsyncServer.class).forEach(server -> tools.addAll(server.listTools().collectList().block()));
        return tools.stream().map(McpSchema.Tool::name).collect(Collectors.toSet());
    }

    private <T> Iterable<T> beans(Class<T> type) {
        return beanFactory.getBeansOfType(type).values();
    }

    /** Tools of MCP servers this application is a client of. Spring AI does not publish them unless asked to. */
    private static boolean isMcpClientTool(ToolCallback tool) {
        return tool instanceof SyncMcpToolCallback || tool instanceof AsyncMcpToolCallback;
    }

    private static boolean isMcpClientToolProvider(ToolCallbackProvider provider) {
        return provider instanceof SyncMcpToolCallbackProvider || provider instanceof AsyncMcpToolCallbackProvider;
    }
}
