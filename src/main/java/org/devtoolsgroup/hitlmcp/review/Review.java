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
