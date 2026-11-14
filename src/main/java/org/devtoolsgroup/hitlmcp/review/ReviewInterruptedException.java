package org.devtoolsgroup.hitlmcp.review;

/**
 * The thread of a tool call was interrupted while waiting for the user, e.g. on server shutdown.
 */
public class ReviewInterruptedException extends RuntimeException {

    public ReviewInterruptedException() {
        super("Invocation cancelled: the server stopped waiting for the user's decision");
    }
}
