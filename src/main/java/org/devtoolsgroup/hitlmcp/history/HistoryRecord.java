package org.devtoolsgroup.hitlmcp.history;

import java.time.Instant;

/**
 * One finished tool invocation. Input and output are the full values; truncation happens when writing.
 */
public record HistoryRecord(
        String id,
        String tool,
        Instant startedAt,
        Instant finishedAt,
        Status status,
        String denialReason,
        String input,
        String output) {

    public enum Status {
        COMPLETED,
        FAILED,
        CALL_DENIED,
        RESULT_DENIED,
        /** The server stopped waiting for the user, e.g. on shutdown. */
        CANCELLED
    }
}
