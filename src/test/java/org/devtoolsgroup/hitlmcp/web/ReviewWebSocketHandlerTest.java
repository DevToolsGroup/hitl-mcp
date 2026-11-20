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

package org.devtoolsgroup.hitlmcp.web;

import org.devtoolsgroup.hitlmcp.review.Decision;
import org.devtoolsgroup.hitlmcp.review.Phase;
import org.devtoolsgroup.hitlmcp.review.ReviewEvent;
import org.devtoolsgroup.hitlmcp.review.ReviewRegistry;
import org.devtoolsgroup.hitlmcp.review.ReviewView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewWebSocketHandlerTest {

    private static final ReviewView VIEW = new ReviewView("r1", "sql", "{}", Instant.parse("2026-11-18T10:00:00Z"),
            Phase.AWAITING_CALL_APPROVAL, null, false);

    private final ReviewRegistry registry = mock(ReviewRegistry.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final ReviewWebSocketHandler handler = new ReviewWebSocketHandler(registry, jsonMapper,
            new UiProperties(123, 4567));

    @BeforeEach
    void setUp() {
        when(registry.snapshot()).thenReturn(List.of(VIEW));
    }

    private static WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private List<JsonNode> sent(WebSocketSession session, int count) throws Exception {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<WebSocketMessage<?>> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, times(count)).sendMessage(captor.capture());
        return captor.getAllValues().stream()
                .map(message -> jsonMapper.readTree(((TextMessage) message).getPayload()))
                .toList();
    }

    private void receive(String payload) {
        handler.handleTextMessage(session("s"), new TextMessage(payload));
    }

    @Test
    void sendsSnapshotOnConnect() throws Exception {
        WebSocketSession session = session("s1");

        handler.afterConnectionEstablished(session);

        JsonNode snapshot = sent(session, 1).getFirst();
        assertThat(snapshot.get("type").asString()).isEqualTo("snapshot");
        assertThat(snapshot.get("pageSize").asInt()).isEqualTo(123);
        assertThat(snapshot.get("maxMessageChars").asInt()).isEqualTo(4567);
        JsonNode review = snapshot.get("reviews").get(0);
        assertThat(review.get("id").asString()).isEqualTo("r1");
        assertThat(review.get("tool").asString()).isEqualTo("sql");
        assertThat(review.get("phase").asString()).isEqualTo("AWAITING_CALL_APPROVAL");
        assertThat(review.get("outputError").asBoolean()).isFalse();
    }

    @Test
    void raisesTheIncomingMessageSizeLimitOnConnect() {
        WebSocketSession session = session("s1");

        handler.afterConnectionEstablished(session);

        verify(session).setTextMessageSizeLimit(4567);
    }

    @Test
    void broadcastsUpdatesToAllConnectedSessions() throws Exception {
        WebSocketSession first = session("s1");
        WebSocketSession second = session("s2");
        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);

        handler.onReviewEvent(new ReviewEvent.Updated(VIEW));

        for (WebSocketSession session : List.of(first, second)) {
            JsonNode upsert = sent(session, 2).getLast();
            assertThat(upsert.get("type").asString()).isEqualTo("upsert");
            assertThat(upsert.get("review").get("id").asString()).isEqualTo("r1");
        }
    }

    @Test
    void broadcastsRemovals() throws Exception {
        WebSocketSession session = session("s1");
        handler.afterConnectionEstablished(session);

        handler.onReviewEvent(new ReviewEvent.Removed("r1"));

        JsonNode remove = sent(session, 2).getLast();
        assertThat(remove.get("type").asString()).isEqualTo("remove");
        assertThat(remove.get("id").asString()).isEqualTo("r1");
    }

    @Test
    void closedSessionsGetNoMoreMessages() throws Exception {
        WebSocketSession session = session("s1");
        handler.afterConnectionEstablished(session);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        clearInvocations(session);

        handler.onReviewEvent(new ReviewEvent.Removed("r1"));

        verify(session, never()).sendMessage(any());
    }

    @Test
    void sendFailureDoesNotAffectOtherSessions() throws Exception {
        WebSocketSession broken = session("broken");
        WebSocketSession healthy = session("healthy");
        handler.afterConnectionEstablished(broken);
        handler.afterConnectionEstablished(healthy);
        doThrow(new IOException("gone")).when(broken).sendMessage(any());

        assertThatCode(() -> handler.onReviewEvent(new ReviewEvent.Removed("r1"))).doesNotThrowAnyException();

        assertThat(sent(healthy, 2).getLast().get("type").asString()).isEqualTo("remove");
    }

    @Test
    void appliesResultDecisionWithUpdatedResult() {
        receive("""
                {"type":"decision","id":"r1","phase":"AWAITING_RESULT_APPROVAL","approved":true,
                 "reason":"","updatedResult":"new text"}""");

        verify(registry).decide("r1", Phase.AWAITING_RESULT_APPROVAL, new Decision(true, "", "new text"));
    }

    @Test
    void dropsUpdatedResultInCallPhase() {
        receive("""
                {"type":"decision","id":"r1","phase":"AWAITING_CALL_APPROVAL","approved":false,
                 "reason":"no","updatedResult":"sneaky"}""");

        verify(registry).decide("r1", Phase.AWAITING_CALL_APPROVAL, new Decision(false, "no", null));
    }

    @Test
    void missingOptionalFieldsAreNull() {
        receive("""
                {"type":"decision","id":"r1","phase":"AWAITING_RESULT_APPROVAL","approved":true}""");

        verify(registry).decide("r1", Phase.AWAITING_RESULT_APPROVAL, new Decision(true, null, null));
    }

    @Test
    void unknownFieldsAreIgnored() {
        receive("""
                {"type":"decision","id":"r1","phase":"AWAITING_CALL_APPROVAL","approved":true,"extra":1}""");

        verify(registry).decide("r1", Phase.AWAITING_CALL_APPROVAL, new Decision(true, null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "{\"type\":\"other\",\"id\":\"r1\",\"phase\":\"AWAITING_CALL_APPROVAL\",\"approved\":true}",
            "{\"type\":\"decision\",\"phase\":\"AWAITING_CALL_APPROVAL\",\"approved\":true}",
            "{\"type\":\"decision\",\"id\":\"r1\",\"approved\":true}",
            "{\"type\":\"decision\",\"id\":\"r1\",\"phase\":\"NO_SUCH_PHASE\",\"approved\":true}",
            // A decision without "approved" is neither an accept nor a deny.
            "{\"type\":\"decision\",\"id\":\"r1\",\"phase\":\"AWAITING_CALL_APPROVAL\"}",
    })
    void ignoresInvalidMessages(String payload) {
        assertThatCode(() -> receive(payload)).doesNotThrowAnyException();

        verify(registry, never()).decide(any(), any(), any());
    }
}
