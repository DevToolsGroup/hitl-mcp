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
