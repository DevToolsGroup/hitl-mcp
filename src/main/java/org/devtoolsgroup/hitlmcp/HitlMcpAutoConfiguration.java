package org.devtoolsgroup.hitlmcp;

import org.devtoolsgroup.hitlmcp.history.HistoryProperties;
import org.devtoolsgroup.hitlmcp.history.HistoryWriter;
import org.devtoolsgroup.hitlmcp.review.ReviewRegistry;
import org.devtoolsgroup.hitlmcp.security.LocalOriginFilter;
import org.devtoolsgroup.hitlmcp.tools.ToolsConfig;
import org.devtoolsgroup.hitlmcp.web.ReviewWebSocketHandler;
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
@Import({ReviewRegistry.class, HistoryWriter.class, ToolsConfig.class, LocalOriginFilter.class,
        ReviewWebSocketHandler.class, WebSocketConfig.class, UiConfig.class})
public class HitlMcpAutoConfiguration {
}
