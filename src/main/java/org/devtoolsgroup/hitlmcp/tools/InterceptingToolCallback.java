package org.devtoolsgroup.hitlmcp.tools;

import org.devtoolsgroup.hitlmcp.history.HistoryRecord;
import org.devtoolsgroup.hitlmcp.history.HistoryRecord.Status;
import org.devtoolsgroup.hitlmcp.history.HistoryWriter;
import org.devtoolsgroup.hitlmcp.review.Decision;
import org.devtoolsgroup.hitlmcp.review.Review;
import org.devtoolsgroup.hitlmcp.review.ReviewDeniedException;
import org.devtoolsgroup.hitlmcp.review.ReviewInterruptedException;
import org.devtoolsgroup.hitlmcp.review.ReviewRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.time.Instant;

/**
 * Decorator around a real tool. Every invocation passes through here, so this is the single place
 * where cross-cutting behaviour is applied: the user approves the call, then approves the result,
 * and the outcome is written to the history file.
 */
@RequiredArgsConstructor
public class InterceptingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ReviewRegistry reviews;
    private final HistoryWriter historyWriter;

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        Review review = reviews.create(delegate.getToolDefinition().name(), toolInput);
        Status status = Status.CANCELLED;
        String output = null;
        String denialReason = null;
        try {
            Decision callDecision = reviews.awaitCallApproval(review);
            if (!callDecision.approved()) {
                status = Status.CALL_DENIED;
                denialReason = callDecision.reason();
                throw new ReviewDeniedException("Invocation denied by user.", denialReason);
            }

            reviews.startExecution(review);
            RuntimeException failure = null;
            try {
                output = toolContext == null ? delegate.call(toolInput) : delegate.call(toolInput, toolContext);
            } catch (RuntimeException e) {
                failure = e;
                output = String.valueOf(e.getMessage());
            }

            Decision resultDecision = reviews.awaitResultApproval(review, output, failure != null);
            if (!resultDecision.approved()) {
                status = Status.RESULT_DENIED;
                denialReason = resultDecision.reason();
                throw new ReviewDeniedException("Result withheld by user.", denialReason);
            }
            if (failure != null) {
                status = Status.FAILED;
                throw failure;
            }
            status = Status.COMPLETED;
            return output;
        } catch (ReviewInterruptedException e) {
            status = Status.CANCELLED;
            throw e;
        } finally {
            reviews.remove(review);
            historyWriter.append(new HistoryRecord(review.getId(), review.getTool(), review.getStartedAt(),
                    Instant.now(), status, denialReason, toolInput, output));
        }
    }
}
