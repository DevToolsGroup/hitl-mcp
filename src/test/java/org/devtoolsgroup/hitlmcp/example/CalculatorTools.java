package org.devtoolsgroup.hitlmcp.example;

import org.devtoolsgroup.hitlmcp.HitlMcpTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Demo tools. They know nothing about reviews or history: the library wraps every tool of a {@link HitlMcpTools} class.
 */
@HitlMcpTools
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
