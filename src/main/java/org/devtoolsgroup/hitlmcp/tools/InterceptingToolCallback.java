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
        String result = null;
        RuntimeException error = null;
        try {
            result = reviewedCall(toolInput, toolContext);
        } catch (RuntimeException e) {
            error = e;
        }
        return returnToAgent(result, error);
    }

    /**
     * The only place that calls the real tool, so a breakpoint here shows exactly what the tool receives.
     * If it is not hit, the tool was not called.
     */
    private String callDelegate(String toolInput, ToolContext toolContext) {
        return toolContext == null ? delegate.call(toolInput) : delegate.call(toolInput, toolContext);
    }

    /**
     * The only exit of {@link #call(String, ToolContext)}, so a breakpoint here shows exactly what the agent gets:
     * either the result or the exception, which the MCP server reports to the agent as a tool error.
     */
    private static String returnToAgent(String result, RuntimeException error) {
        if (error != null) {
            throw error;
        }
        return result;
    }

    private String reviewedCall(String toolInput, ToolContext toolContext) {
        Review review = reviews.create(delegate.getToolDefinition().name(), toolInput);
        Status status = Status.CANCELLED;
        String output = null;
        String updatedResult = null;
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
                output = callDelegate(toolInput, toolContext);
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
            if (resultDecision.hasUpdatedResult()) {
                // The reviewer's text replaces the tool output, also when the tool failed.
                updatedResult = resultDecision.updatedResult();
                status = Status.COMPLETED;
                return updatedResult;
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
                    Instant.now(), status, denialReason, toolInput, output, updatedResult));
        }
    }
}
