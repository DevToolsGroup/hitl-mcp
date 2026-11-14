package org.devtoolsgroup.hitlmcp.review;

import java.time.Instant;

/**
 * Immutable snapshot of a {@link Review}, sent to the browser.
 */
public record ReviewView(
        String id,
        String tool,
        String input,
        Instant startedAt,
        Phase phase,
        String output,
        boolean outputError) {
}
