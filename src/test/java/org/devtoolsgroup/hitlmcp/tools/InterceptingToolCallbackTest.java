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
import org.devtoolsgroup.hitlmcp.review.Phase;
import org.devtoolsgroup.hitlmcp.review.ReviewDeniedException;
import org.devtoolsgroup.hitlmcp.review.ReviewEvent;
import org.devtoolsgroup.hitlmcp.review.ReviewInterruptedException;
import org.devtoolsgroup.hitlmcp.review.ReviewProperties;
import org.devtoolsgroup.hitlmcp.review.ReviewRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives {@link InterceptingToolCallback} through a real {@link ReviewRegistry}.
 * The registry's event publisher plays the reviewer: whenever a review waits for a decision,
 * it answers with the next scripted {@link Decision}.
 */
@Timeout(10)
class InterceptingToolCallbackTest {

    private static final String PREFIX = "modified by reviewer: ";
    private static final String INPUT = "{\"query\":\"select 1\"}";

    private final Deque<Decision> decisions = new ArrayDeque<>();
    private final List<Object> events = new ArrayList<>();
    private final ToolCallback delegate = mock(ToolCallback.class);
    private final HistoryWriter historyWriter = mock(HistoryWriter.class);
    private ReviewRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ReviewRegistry(event -> {
            events.add(event);
            if (event instanceof ReviewEvent.Updated updated
                    && updated.review().phase() != Phase.EXECUTING && !decisions.isEmpty()) {
                registry.decide(updated.review().id(), updated.review().phase(), decisions.poll());
            }
        });
        when(delegate.getToolDefinition()).thenReturn(
                ToolDefinition.builder().name("sql").description("Runs SQL").inputSchema("{}").build());
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    private InterceptingToolCallback callback(String prefix) {
        return new InterceptingToolCallback(delegate, registry, historyWriter, new ReviewProperties(prefix));
    }

    private InterceptingToolCallback callback() {
        return callback(PREFIX);
    }

    private HistoryRecord history() {
        ArgumentCaptor<HistoryRecord> captor = ArgumentCaptor.forClass(HistoryRecord.class);
        verify(historyWriter).append(captor.capture());
        return captor.getValue();
    }

    @Test
    void delegatesDefinitionAndMetadata() {
        InterceptingToolCallback callback = callback();

        assertThat(callback.getToolDefinition().name()).isEqualTo("sql");
        callback.getToolMetadata();
        verify(delegate).getToolMetadata();
    }

    @Test
    void approvedCallAndResultReturnToolOutput() {
        when(delegate.call(INPUT)).thenReturn("42");
        decisions.add(new Decision(true, null));
        decisions.add(new Decision(true, null));

        String result = callback().call(INPUT);

        assertThat(result).isEqualTo("42");
        HistoryRecord record = history();
        assertThat(record.status()).isEqualTo(Status.COMPLETED);
        assertThat(record.tool()).isEqualTo("sql");
        assertThat(record.input()).isEqualTo(INPUT);
        assertThat(record.output()).isEqualTo("42");
        assertThat(record.updatedResult()).isNull();
        assertThat(record.denialReason()).isNull();
        assertThat(record.finishedAt()).isAfterOrEqualTo(record.startedAt());
    }

    @Test
    void reviewGoesThroughAllPhasesAndIsRemovedAtTheEnd() {
        when(delegate.call(INPUT)).thenReturn("42");
        decisions.add(new Decision(true, null));
        decisions.add(new Decision(true, null));

        callback().call(INPUT);

        List<Phase> phases = events.stream()
                .filter(ReviewEvent.Updated.class::isInstance)
                .map(event -> ((ReviewEvent.Updated) event).review().phase())
                .toList();
        assertThat(phases).containsExactly(
                Phase.AWAITING_CALL_APPROVAL, Phase.EXECUTING, Phase.AWAITING_RESULT_APPROVAL);
        assertThat(events.getLast()).isInstanceOf(ReviewEvent.Removed.class);
        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void resultUnderReviewIsTheToolOutput() {
        when(delegate.call(INPUT)).thenReturn("42");
        decisions.add(new Decision(true, null));
        decisions.add(new Decision(true, null));

        callback().call(INPUT);

        ReviewEvent.Updated resultReview = events.stream()
                .filter(ReviewEvent.Updated.class::isInstance)
                .map(ReviewEvent.Updated.class::cast)
                .filter(event -> event.review().phase() == Phase.AWAITING_RESULT_APPROVAL)
                .findFirst().orElseThrow();
        assertThat(resultReview.review().output()).isEqualTo("42");
        assertThat(resultReview.review().outputError()).isFalse();
        assertThat(resultReview.review().input()).isEqualTo(INPUT);
    }

    @Test
    void deniedCallNeverReachesTheTool() {
        decisions.add(new Decision(false, "  too dangerous  "));

        assertThatThrownBy(() -> callback().call(INPUT))
                .isInstanceOf(ReviewDeniedException.class)
                .hasMessage("Invocation denied by user. Reason: too dangerous");

        verify(delegate, never()).call(anyString());
        verify(delegate, never()).call(anyString(), any());
        HistoryRecord record = history();
        assertThat(record.status()).isEqualTo(Status.CALL_DENIED);
        assertThat(record.denialReason()).isEqualTo("  too dangerous  ");
        assertThat(record.output()).isNull();
        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void deniedCallWithoutReason() {
        decisions.add(new Decision(false, ""));

        assertThatThrownBy(() -> callback().call(INPUT))
                .isInstanceOf(ReviewDeniedException.class)
                .hasMessage("Invocation denied by user.");
    }

    @Test
    void deniedResultIsWithheldFromTheAgent() {
        when(delegate.call(INPUT)).thenReturn("secret");
        decisions.add(new Decision(true, null));
        decisions.add(new Decision(false, "contains secrets"));

        assertThatThrownBy(() -> callback().call(INPUT))
                .isInstanceOf(ReviewDeniedException.class)
                .hasMessage("Result withheld by user. Reason: contains secrets")
                .hasMessageNotContaining("secret\"");

        HistoryRecord record = history();
        assertThat(record.status()).isEqualTo(Status.RESULT_DENIED);
        assertThat(record.denialReason()).isEqualTo("contains secrets");
        assertThat(record.output()).isEqualTo("secret");
    }

    @Test
    void deniedResultIgnoresUpdatedResult() {
        when(delegate.call(INPUT)).thenReturn("42");
        decisions.add(new Decision(true, null));
        decisions.add(new Decision(false, null, "replacement"));

        assertThatThrownBy(() -> callback().call(INPUT)).isInstanceOf(ReviewDeniedException.class);

        assertThat(history().updatedResult()).isNull();
    }

    @Test
    void updatedResultIsReturnedWithPrefix() {
        when(delegate.call(INPUT)).thenReturn("42");
        decisions.add(new Decision(true, null));
        decisions.add(new Decision(true, null, "43"));

        String result = callback().call(INPUT);

        assertThat(result).isEqualTo(PREFIX + "43");
        HistoryRecord record = history();
        assertThat(record.status()).isEqualTo(Status.COMPLETED);
        assertThat(record.output()).isEqualTo("42");
        assertThat(record.updatedResult()).as("history keeps the reviewer's text without the prefix")
                .isEqualTo("43");
    }

    @Test
    void emptyPrefixReturnsUpdatedResultAsIs() {
        when(delegate.call(INPUT)).thenReturn("42");
        decisions.add(new Decision(true, null));
        decisions.add(new Decision(true, null, "43"));

        assertThat(callback("").call(INPUT)).isEqualTo("43");
    }

    @Test
    void prefixIsNotAddedToUnchangedResult() {
        when(delegate.call(INPUT)).thenReturn("42");
        decisions.add(new Decision(true, null));
        decisions.add(new Decision(true, null, "   "));

        assertThat(callback().call(INPUT)).as("blank updated result means keep the tool's result").isEqualTo("42");
        assertThat(history().updatedResult()).isNull();
    }

    @Test
    void toolFailureAcceptedByReviewerIsRethrownAsIs() {
        IllegalArgumentException failure = new IllegalArgumentException("bad SQL");
        when(delegate.call(INPUT)).thenThrow(failure);
        decisions.add(new Decision(true, null));
        decisions.add(new Decision(true, null));

        assertThatThrownBy(() -> callback().call(INPUT)).isSameAs(failure);

        HistoryRecord record = history();
        assertThat(record.status()).isEqualTo(Status.FAILED);
        assertThat(record.output()).isEqualTo("bad SQL");
    }

    @Test
    void toolFailureIsShownToReviewerAsError() {
        when(delegate.call(INPUT)).thenThrow(new IllegalStateException("boom"));
        decisions.add(new Decision(true, null));
        decisions.add(new Decision(true, null));

        assertThatThrownBy(() -> callback().call(INPUT)).isInstanceOf(IllegalStateException.class);

        ReviewEvent.Updated resultReview = events.stream()
                .filter(ReviewEvent.Updated.class::isInstance)
                .map(ReviewEvent.Updated.class::cast)
                .filter(event -> event.review().phase() == Phase.AWAITING_RESULT_APPROVAL)
                .findFirst().orElseThrow();
        assertThat(resultReview.review().output()).isEqualTo("boom");
        assertThat(resultReview.review().outputError()).isTrue();
    }

    @Test
    void toolFailureWithoutMessageIsShownAsNull() {
        when(delegate.call(INPUT)).thenThrow(new IllegalStateException());
        decisions.add(new Decision(true, null));
        decisions.add(new Decision(true, null));

        assertThatThrownBy(() -> callback().call(INPUT)).isInstanceOf(IllegalStateException.class);

        assertThat(history().output()).isEqualTo("null");
    }

    @Test
    void toolFailureReplacedByReviewerReturnsUpdatedResult() {
        when(delegate.call(INPUT)).thenThrow(new IllegalStateException("boom"));
        decisions.add(new Decision(true, null));
        decisions.add(new Decision(true, null, "fallback"));

        assertThat(callback().call(INPUT)).isEqualTo(PREFIX + "fallback");

        HistoryRecord record = history();
        assertThat(record.status()).isEqualTo(Status.COMPLETED);
        assertThat(record.output()).isEqualTo("boom");
        assertThat(record.updatedResult()).isEqualTo("fallback");
    }

    @Test
    void toolFailureDeniedByReviewer() {
        when(delegate.call(INPUT)).thenThrow(new IllegalStateException("boom"));
        decisions.add(new Decision(true, null));
        decisions.add(new Decision(false, "no"));

        assertThatThrownBy(() -> callback().call(INPUT))
                .isInstanceOf(ReviewDeniedException.class)
                .hasMessage("Result withheld by user. Reason: no");
        assertThat(history().status()).isEqualTo(Status.RESULT_DENIED);
    }

    @Test
    void toolContextIsPassedToTheTool() {
        ToolContext context = new ToolContext(Map.of("user", "u1"));
        when(delegate.call(INPUT, context)).thenReturn("with context");
        decisions.add(new Decision(true, null));
        decisions.add(new Decision(true, null));

        assertThat(callback().call(INPUT, context)).isEqualTo("with context");

        verify(delegate).call(INPUT, context);
        verify(delegate, never()).call(anyString());
    }

    @Test
    void callWithoutContextUsesSingleArgumentToolCall() {
        when(delegate.call(INPUT)).thenReturn("42");
        decisions.add(new Decision(true, null));
        decisions.add(new Decision(true, null));

        callback().call(INPUT, null);

        verify(delegate).call(INPUT);
        verify(delegate, never()).call(anyString(), any());
    }

    @Test
    void interruptedWaitCancelsTheCall() {
        // No scripted decision: the call would wait forever, but the thread is already interrupted.
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> callback().call(INPUT)).isInstanceOf(ReviewInterruptedException.class);

        assertThat(Thread.currentThread().isInterrupted()).as("interrupt flag is restored").isTrue();
        verify(delegate, never()).call(anyString());
        assertThat(history().status()).isEqualTo(Status.CANCELLED);
        assertThat(registry.snapshot()).isEmpty();
    }
}
