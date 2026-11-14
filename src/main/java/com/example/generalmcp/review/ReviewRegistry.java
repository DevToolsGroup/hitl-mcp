package com.example.generalmcp.review;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * Holds the pending reviews and connects the blocked tool calls with the user's decisions.
 * Every change is published as a {@link ReviewEvent}, which the WebSocket handler forwards to the browser.
 */
@Component
@RequiredArgsConstructor
public class ReviewRegistry {

    private final Map<String, Review> reviews = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher events;

    /** Registers a new invocation, waiting for call approval. */
    public Review create(String tool, String input) {
        Review review = new Review(UUID.randomUUID().toString(), tool, input, Instant.now());
        reviews.put(review.getId(), review);
        publishUpdate(review);
        return review;
    }

    /** Blocks until the user decides whether the tool may be called. */
    public Decision awaitCallApproval(Review review) {
        return await(review.currentDecision());
    }

    public void startExecution(Review review) {
        review.startExecution();
        publishUpdate(review);
    }

    /** Shows the result to the user and blocks until they decide whether it may be returned. */
    public Decision awaitResultApproval(Review review, String output, boolean outputError) {
        CompletableFuture<Decision> decision = review.awaitResultApproval(output, outputError);
        publishUpdate(review);
        return await(decision);
    }

    public void remove(Review review) {
        if (reviews.remove(review.getId()) != null) {
            events.publishEvent(new ReviewEvent.Removed(review.getId()));
        }
    }

    /**
     * Applies a decision coming from the browser.
     *
     * @return false if the review does not exist or is not in the given phase any more
     */
    public boolean decide(String id, Phase phase, Decision decision) {
        Review review = reviews.get(id);
        return review != null && review.decide(phase, decision);
    }

    /** All pending reviews, oldest first. */
    public List<ReviewView> snapshot() {
        return reviews.values().stream()
                .map(Review::toView)
                .sorted(Comparator.comparing(ReviewView::startedAt))
                .toList();
    }

    private void publishUpdate(Review review) {
        events.publishEvent(new ReviewEvent.Updated(review.toView()));
    }

    private static Decision await(CompletableFuture<Decision> decision) {
        try {
            return decision.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReviewInterruptedException();
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }
}
