package com.example.generalmcp.review;

/**
 * Thrown to the MCP layer when the user denies a call or withholds a result.
 * Spring AI turns it into a tool result with {@code isError=true} and this message.
 */
public class ReviewDeniedException extends RuntimeException {

    public ReviewDeniedException(String message, String reason) {
        super(reason == null || reason.isBlank() ? message : message + " Reason: " + reason.strip());
    }
}
