package org.devtoolsgroup.hitlmcp.tools;

import org.devtoolsgroup.hitlmcp.HitlMcpTools;
import org.devtoolsgroup.hitlmcp.history.HistoryWriter;
import org.devtoolsgroup.hitlmcp.review.ReviewRegistry;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class ToolsConfig {

    /**
     * The only tool source the MCP server sees: every {@code @Tool} method of every {@link HitlMcpTools} bean,
     * wrapped in {@link InterceptingToolCallback}.
     * <p>
     * Spring AI also publishes any other {@link ToolCallbackProvider} or {@link ToolCallback} bean, without review,
     * so applications must not declare such beans.
     */
    @Bean
    public ToolCallbackProvider interceptedTools(ListableBeanFactory beanFactory, ReviewRegistry reviews,
                                                 HistoryWriter historyWriter) {
        Object[] toolObjects = beanFactory.getBeansWithAnnotation(HitlMcpTools.class).values().toArray();
        ToolCallback[] tools = MethodToolCallbackProvider.builder()
                .toolObjects(toolObjects)
                .build()
                .getToolCallbacks();
        return ToolCallbackProvider.from(Arrays.stream(tools)
                .map(tool -> (ToolCallback) new InterceptingToolCallback(tool, reviews, historyWriter))
                .toList());
    }
}
