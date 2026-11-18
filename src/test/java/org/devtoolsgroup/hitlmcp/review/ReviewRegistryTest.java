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

package org.devtoolsgroup.hitlmcp.review;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(10)
class ReviewRegistryTest {

    private final List<Object> events = new CopyOnWriteArrayList<>();
    private final ReviewRegistry registry = new ReviewRegistry(events::add);

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void createRegistersReviewAwaitingCallApproval() {
        Review review = registry.create("tool", "input");

        assertThat(review.getId()).isNotBlank();
        assertThat(review.getPhase()).isEqualTo(Phase.AWAITING_CALL_APPROVAL);
        assertThat(registry.snapshot()).singleElement().satisfies(view -> {
            assertThat(view.id()).isEqualTo(review.getId());
            assertThat(view.tool()).isEqualTo("tool");
            assertThat(view.input()).isEqualTo("input");
            assertThat(view.phase()).isEqualTo(Phase.AWAITING_CALL_APPROVAL);
            assertThat(view.output()).isNull();
        });
        assertThat(events).singleElement().isInstanceOf(ReviewEvent.Updated.class);
    }

    @Test
    void reviewsGetUniqueIds() {
        assertThat(registry.create("t", "a").getId()).isNotEqualTo(registry.create("t", "a").getId());
        assertThat(registry.snapshot()).hasSize(2);
    }

    @Test
    void awaitCallApprovalBlocksUntilDecided() throws Exception {
        Review review = registry.create("tool", "input");
        CompletableFuture<Decision> waiting = CompletableFuture.supplyAsync(() -> registry.awaitCallApproval(review));

        assertThat(waiting).isNotDone();
        Decision decision = new Decision(true, "ok");
        assertThat(registry.decide(review.getId(), Phase.AWAITING_CALL_APPROVAL, decision)).isTrue();

        assertThat(waiting.get(5, TimeUnit.SECONDS)).isEqualTo(decision);
    }

    @Test
    void firstDecisionWins() {
        Review review = registry.create("tool", "input");

        assertThat(registry.decide(review.getId(), Phase.AWAITING_CALL_APPROVAL, new Decision(true, null))).isTrue();
        assertThat(registry.decide(review.getId(), Phase.AWAITING_CALL_APPROVAL, new Decision(false, null))).isFalse();

        assertThat(registry.awaitCallApproval(review).approved()).isTrue();
    }

    @Test
    void decisionForUnknownReviewIsIgnored() {
        assertThat(registry.decide("missing", Phase.AWAITING_CALL_APPROVAL, new Decision(true, null))).isFalse();
    }

    @Test
    void decisionForAnotherPhaseIsIgnored() {
        Review review = registry.create("tool", "input");

        assertThat(registry.decide(review.getId(), Phase.AWAITING_RESULT_APPROVAL, new Decision(true, null)))
                .isFalse();
    }

    @Test
    void nothingCanBeDecidedWhileExecuting() {
        Review review = registry.create("tool", "input");
        registry.decide(review.getId(), Phase.AWAITING_CALL_APPROVAL, new Decision(true, null));
        registry.startExecution(review);

        assertThat(review.getPhase()).isEqualTo(Phase.EXECUTING);
        assertThat(registry.decide(review.getId(), Phase.EXECUTING, new Decision(true, null))).isFalse();
        assertThat(registry.decide(review.getId(), Phase.AWAITING_CALL_APPROVAL, new Decision(true, null)))
                .isFalse();
        assertThat(((ReviewEvent.Updated) events.getLast()).review().phase()).isEqualTo(Phase.EXECUTING);
    }

    @Test
    void resultApprovalShowsOutputAndWaitsForNewDecision() throws Exception {
        Review review = registry.create("tool", "input");
        registry.decide(review.getId(), Phase.AWAITING_CALL_APPROVAL, new Decision(true, null));
        registry.awaitCallApproval(review);
        registry.startExecution(review);

        CompletableFuture<Decision> waiting = CompletableFuture.supplyAsync(
                () -> registry.awaitResultApproval(review, "output", true));
        // Wait until the result is published; the earlier call decision must not satisfy the result phase.
        while (review.getPhase() != Phase.AWAITING_RESULT_APPROVAL) {
            Thread.onSpinWait();
        }
        assertThat(waiting).isNotDone();

        ReviewView view = registry.snapshot().getFirst();
        assertThat(view.phase()).isEqualTo(Phase.AWAITING_RESULT_APPROVAL);
        assertThat(view.output()).isEqualTo("output");
        assertThat(view.outputError()).isTrue();

        Decision decision = new Decision(true, null, "updated");
        assertThat(registry.decide(review.getId(), Phase.AWAITING_RESULT_APPROVAL, decision)).isTrue();
        assertThat(waiting.get(5, TimeUnit.SECONDS)).isEqualTo(decision);
    }

    @Test
    void removePublishesRemovedOnce() {
        Review review = registry.create("tool", "input");
        events.clear();

        registry.remove(review);
        registry.remove(review);

        assertThat(events).containsExactly(new ReviewEvent.Removed(review.getId()));
        assertThat(registry.snapshot()).isEmpty();
        assertThat(registry.decide(review.getId(), Phase.AWAITING_CALL_APPROVAL, new Decision(true, null)))
                .isFalse();
    }

    @Test
    void interruptedWaitThrowsAndKeepsInterruptFlag() {
        Review review = registry.create("tool", "input");
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> registry.awaitCallApproval(review)).isInstanceOf(ReviewInterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void snapshotIsOrderedByStartTime() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ids.add(registry.create("tool", "input " + i).getId());
        }

        List<ReviewView> snapshot = registry.snapshot();

        assertThat(snapshot).extracting(ReviewView::startedAt).isSorted();
        assertThat(snapshot).extracting(ReviewView::id).containsExactlyInAnyOrderElementsOf(ids);
    }
}
