package org.devtoolsgroup.hitlmcp;

import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class whose Spring AI {@code @Tool} methods are published by the MCP server, each call reviewed by the user.
 * {@code @Tool} methods of classes without this annotation are ignored.
 * <p>
 * The annotation is also a {@link Component}, so Spring Boot applications pick such classes up by component scan.
 * Plain Java applications pass instances to {@link HitlMcpServer.Builder#tools(Object...)}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface HitlMcpTools {
}
