package com.example.generalmcp.tools;

import com.example.generalmcp.history.HistoryWriter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class ToolsConfig {

    /**
     * The only tool source the MCP server sees: every {@code @Tool} method, wrapped in {@link InterceptingToolCallback}.
     * To add tools, pass more objects to {@code toolObjects(...)}.
     */
    @Bean
    public ToolCallbackProvider interceptedTools(CalculatorTools calculatorTools, HistoryWriter historyWriter) {
        ToolCallback[] tools = MethodToolCallbackProvider.builder()
                .toolObjects(calculatorTools)
                .build()
                .getToolCallbacks();
        return ToolCallbackProvider.from(Arrays.stream(tools)
                .map(tool -> (ToolCallback) new InterceptingToolCallback(tool, historyWriter))
                .toList());
    }
}
