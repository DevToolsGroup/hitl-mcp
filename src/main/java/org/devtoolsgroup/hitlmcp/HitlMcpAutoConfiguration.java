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

package org.devtoolsgroup.hitlmcp;

import org.devtoolsgroup.hitlmcp.history.HistoryProperties;
import org.devtoolsgroup.hitlmcp.history.HistoryWriter;
import org.devtoolsgroup.hitlmcp.review.ReviewRegistry;
import org.devtoolsgroup.hitlmcp.security.LocalOriginFilter;
import org.devtoolsgroup.hitlmcp.tools.ToolsConfig;
import org.devtoolsgroup.hitlmcp.tools.UnreviewedToolsCheck;
import org.devtoolsgroup.hitlmcp.web.ReviewWebSocketHandler;
import org.devtoolsgroup.hitlmcp.web.StartupUrlsPrinter;
import org.devtoolsgroup.hitlmcp.web.UiConfig;
import org.devtoolsgroup.hitlmcp.web.UiProperties;
import org.devtoolsgroup.hitlmcp.web.WebSocketConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Sets up the human-in-the-loop MCP server in any Spring Boot application that has this library on the classpath.
 * Registered in {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({HistoryProperties.class, UiProperties.class})
@Import({ReviewRegistry.class, HistoryWriter.class, ToolsConfig.class, UnreviewedToolsCheck.class,
        LocalOriginFilter.class, ReviewWebSocketHandler.class, WebSocketConfig.class, UiConfig.class,
        StartupUrlsPrinter.class})
public class HitlMcpAutoConfiguration {
}
