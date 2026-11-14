package com.example.generalmcp.review;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * One tool invocation under review. Mutated only by {@link ReviewRegistry}, under the lock of this object.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class Review {

    private final String id;
    private final String tool;
    private final String input;
    private final Instant startedAt;

    private Phase phase = Phase.AWAITING_CALL_APPROVAL;
    private String output;
    private boolean outputError;

    @Getter(AccessLevel.NONE)
    private CompletableFuture<Decision> decision = new CompletableFuture<>();

    synchronized void startExecution() {
        phase = Phase.EXECUTING;
    }

    synchronized CompletableFuture<Decision> awaitResultApproval(String output, boolean outputError) {
        this.output = output;
        this.outputError = outputError;
        this.phase = Phase.AWAITING_RESULT_APPROVAL;
        this.decision = new CompletableFuture<>();
        return decision;
    }

    synchronized CompletableFuture<Decision> currentDecision() {
        return decision;
    }

    /**
     * Completes the pending decision if the review is still in the given phase.
     * The first decision wins; late, duplicate or out-of-phase decisions are ignored.
     */
    synchronized boolean decide(Phase expectedPhase, Decision value) {
        if (phase != expectedPhase || phase == Phase.EXECUTING) {
            return false;
        }
        return decision.complete(value);
    }

    synchronized ReviewView toView() {
        return new ReviewView(id, tool, input, startedAt, phase, output, outputError);
    }
}
