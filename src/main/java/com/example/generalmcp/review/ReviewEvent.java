package com.example.generalmcp.review;

/**
 * Published by {@link ReviewRegistry} whenever the set of pending reviews changes.
 */
public sealed interface ReviewEvent {

    record Updated(ReviewView review) implements ReviewEvent {
    }

    record Removed(String id) implements ReviewEvent {
    }
}
