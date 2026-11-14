package org.devtoolsgroup.hitlmcp.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Demo tools. They know nothing about reviews or history, see {@link InterceptingToolCallback}.
 */
@Component
public class CalculatorTools {

    @Tool(name = "add", description = "Adds two numbers and returns the sum")
    public double add(@ToolParam(description = "First addend") double a,
                      @ToolParam(description = "Second addend") double b) {
        return a + b;
    }

    @Tool(name = "multiply", description = "Multiplies two numbers and returns the product")
    public double multiply(@ToolParam(description = "First factor") double a,
                           @ToolParam(description = "Second factor") double b) {
        return a * b;
    }
}
