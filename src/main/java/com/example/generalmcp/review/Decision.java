package com.example.generalmcp.review;

/**
 * @param approved true if the user pressed Accept
 * @param reason   optional text entered by the user, sent to the agent on denial
 */
public record Decision(boolean approved, String reason) {
}
