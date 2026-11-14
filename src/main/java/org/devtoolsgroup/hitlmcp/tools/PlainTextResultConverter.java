package org.devtoolsgroup.hitlmcp.tools;

import org.springframework.ai.tool.execution.ToolCallResultConverter;

import java.lang.reflect.Type;

/**
 * Returns a tool's String result as is. Spring AI's default converter JSON-encodes it, wrapping the text in quotes.
 */
public class PlainTextResultConverter implements ToolCallResultConverter {

    @Override
    public String convert(Object result, Type returnType) {
        return result == null ? "" : result.toString();
    }
}
