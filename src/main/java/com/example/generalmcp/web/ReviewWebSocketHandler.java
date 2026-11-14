package com.example.generalmcp.web;

import com.example.generalmcp.review.Decision;
import com.example.generalmcp.review.Phase;
import com.example.generalmcp.review.ReviewEvent;
import com.example.generalmcp.review.ReviewRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pushes pending reviews to the browser pages and receives the user's decisions.
 * See the "WebSocket protocol" section of doc/mcp-design.md.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewWebSocketHandler extends TextWebSocketHandler {

    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT = 64 * 1024 * 1024;

    private final ReviewRegistry registry;
    private final JsonMapper jsonMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    record DecisionMessage(String type, String id, Phase phase, boolean approved, String reason) {
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // The decorator makes sending thread-safe; broadcasts come from many tool-call threads.
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
        sessions.put(session.getId(), safe);
        send(safe, Map.of("type", "snapshot", "reviews", registry.snapshot()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            DecisionMessage msg = jsonMapper.readValue(message.getPayload(), DecisionMessage.class);
            if (!"decision".equals(msg.type()) || msg.id() == null || msg.phase() == null) {
                log.warn("Ignoring unexpected message: {}", message.getPayload());
                return;
            }
            if (!registry.decide(msg.id(), msg.phase(), new Decision(msg.approved(), msg.reason()))) {
                log.debug("Decision for {} in phase {} ignored (already decided or gone)", msg.id(), msg.phase());
            }
        } catch (JacksonException e) {
            log.warn("Ignoring malformed message: {}", message.getPayload());
        }
    }

    @EventListener
    public void onReviewEvent(ReviewEvent event) {
        Map<String, Object> message = switch (event) {
            case ReviewEvent.Updated updated -> Map.of("type", "upsert", "review", updated.review());
            case ReviewEvent.Removed removed -> Map.of("type", "remove", "id", removed.id());
        };
        sessions.values().forEach(session -> send(session, message));
    }

    private void send(WebSocketSession session, Object message) {
        try {
            session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(message)));
        } catch (IOException | IllegalStateException e) {
            log.debug("Cannot send to WebSocket session {}: {}", session.getId(), e.getMessage());
        }
    }
}
