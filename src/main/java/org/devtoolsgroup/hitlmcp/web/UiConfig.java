package org.devtoolsgroup.hitlmcp.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the review page at {@code /hitlmcp/}. The files live in {@code static/hitlmcp/},
 * a separate folder so they never clash with the application's own static files.
 */
@Configuration
public class UiConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/hitlmcp", "/hitlmcp/");
        registry.addViewController("/hitlmcp/").setViewName("forward:/hitlmcp/index.html");
    }
}
