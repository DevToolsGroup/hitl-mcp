package org.devtoolsgroup.hitlmcp.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param pageSize how many characters of an input or output are shown on one page of a card
 */
@ConfigurationProperties("hitlmcp.ui")
public record UiProperties(int pageSize) {

    public UiProperties {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("hitlmcp.ui.page-size must be positive, got " + pageSize);
        }
    }
}
