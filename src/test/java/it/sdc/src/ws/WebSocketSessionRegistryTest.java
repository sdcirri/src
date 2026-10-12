package it.sdc.src.ws;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class WebSocketSessionRegistryTest {
    private WebSocketSessionRegistry registry;
    private Map<UUID, Set<WebSocketSession>> innerSessionMap;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = new WebSocketSessionRegistry();
        innerSessionMap = (Map<UUID, Set<WebSocketSession>>) ReflectionTestUtils.getField(registry, "sessions");
    }

    @Test
    void register_addsSocketForSession() {
        UUID sessionId = UUID.randomUUID();
        WebSocketSession socket = mock(WebSocketSession.class);

        registry.register(sessionId, socket);

        assertThat(innerSessionMap).containsKey(sessionId);
        assertThat(innerSessionMap.get(sessionId)).containsExactlyInAnyOrder(socket);
    }

    @Test
    void register_allowsMultipleSocketsForSameSession() {
        UUID sessionId = UUID.randomUUID();
        WebSocketSession socket1 = mock(WebSocketSession.class), socket2 = mock(WebSocketSession.class);

        registry.register(sessionId, socket1);
        registry.register(sessionId, socket2);

        assertThat(innerSessionMap).containsKey(sessionId);
        assertThat(innerSessionMap.get(sessionId)).containsExactlyInAnyOrder(socket1, socket2);
    }

    @Test
    void register_keepsSessionsIsolated() {
        UUID session1Id = UUID.randomUUID(), session2Id = UUID.randomUUID();
        WebSocketSession socket1 = mock(WebSocketSession.class), socket2 = mock(WebSocketSession.class);

        registry.register(session1Id, socket1);
        registry.register(session2Id, socket2);

        assertThat(innerSessionMap).containsKeys(session1Id, session2Id);
        assertThat(innerSessionMap.get(session1Id)).containsExactlyInAnyOrder(socket1);
        assertThat(innerSessionMap.get(session2Id)).containsExactlyInAnyOrder(socket2);
    }

    @Test
    void register_sameSocketTwice_doesNotDuplicate() {
        UUID sessionId = UUID.randomUUID();
        WebSocketSession socket = mock(WebSocketSession.class);

        registry.register(sessionId, socket);
        registry.register(sessionId, socket);

        assertThat(innerSessionMap).containsKey(sessionId);
        assertThat(innerSessionMap.get(sessionId)).hasSize(1);
        assertThat(innerSessionMap.get(sessionId)).containsExactlyInAnyOrder(socket);
    }

    @Test
    void unregister_oneSocket_removesSocketAndKeepsOtherSockets() {
        UUID sessionId = UUID.randomUUID();
        WebSocketSession socket1 = mock(WebSocketSession.class), socket2 = mock(WebSocketSession.class);

        registry.register(sessionId, socket1);
        registry.register(sessionId, socket2);
        registry.unregister(sessionId, socket1);

        assertThat(innerSessionMap).containsKey(sessionId);
        assertThat(innerSessionMap.get(sessionId)).containsExactlyInAnyOrder(socket2);
    }

    @Test
    void unregister_lastSocket_removesSessionEntry() {
        UUID sessionId = UUID.randomUUID();
        WebSocketSession socket = mock(WebSocketSession.class);

        registry.register(sessionId, socket);
        registry.unregister(sessionId, socket);

        assertThat(innerSessionMap).doesNotContainKey(sessionId);
    }

    @Test
    void unregister_unknownSession_doesNothing() {
        UUID sessionId = UUID.randomUUID();
        WebSocketSession socket = mock(WebSocketSession.class);

        Set<UUID> keys = Set.copyOf(innerSessionMap.keySet());
        registry.unregister(sessionId, socket);
        assertThat(innerSessionMap.keySet()).isEqualTo(keys);
    }

    @Test
    void unregister_unknownSocket_doesNothing() {
        UUID sessionId = UUID.randomUUID();
        WebSocketSession socket1 = mock(WebSocketSession.class), socket2 = mock(WebSocketSession.class);

        registry.register(sessionId, socket1);
        registry.unregister(sessionId, socket2);

        assertThat(innerSessionMap.get(sessionId)).containsExactlyInAnyOrder(socket1);
    }

    @Test
    void revoke_closesAllOpenSocketsForSession() throws IOException {
        UUID sessionId = UUID.randomUUID();
        WebSocketSession socket1 = mock(WebSocketSession.class), socket2 = mock(WebSocketSession.class);

        registry.register(sessionId, socket1);
        registry.register(sessionId, socket2);
        when(socket1.isOpen()).thenReturn(true);
        when(socket2.isOpen()).thenReturn(true);

        registry.revoke(sessionId);

        verify(socket1).close(CloseStatus.POLICY_VIOLATION);
        verify(socket2).close(CloseStatus.POLICY_VIOLATION);
        assertThat(innerSessionMap).doesNotContainKey(sessionId);
    }

    @Test
    void revoke_doesNotCloseSocketsFromOtherSessions() throws IOException {
        UUID session1Id = UUID.randomUUID(), session2Id = UUID.randomUUID();
        WebSocketSession socket11 = mock(WebSocketSession.class), socket12 = mock(WebSocketSession.class);
        WebSocketSession socket21 = mock(WebSocketSession.class), socket22 = mock(WebSocketSession.class);

        registry.register(session1Id, socket11);
        registry.register(session1Id, socket12);
        registry.register(session2Id, socket21);
        registry.register(session2Id, socket22);
        when(socket11.isOpen()).thenReturn(true);
        when(socket12.isOpen()).thenReturn(true);
        when(socket21.isOpen()).thenReturn(true);
        when(socket22.isOpen()).thenReturn(true);

        registry.revoke(session1Id);

        verify(socket11).close(CloseStatus.POLICY_VIOLATION);
        verify(socket12).close(CloseStatus.POLICY_VIOLATION);

        verify(socket21, never()).close(any());
        verify(socket22, never()).close(any());

        assertThat(innerSessionMap).doesNotContainKey(session1Id);
        assertThat(innerSessionMap).containsKey(session2Id);
        assertThat(innerSessionMap.get(session2Id)).containsExactlyInAnyOrder(socket21, socket22);
    }

    @Test
    void revoke_unknownSession_doesNothing() {
        registry.revoke(UUID.randomUUID());
        assertThat(innerSessionMap.keySet()).isEmpty();
    }

    @Test
    void revoke_continuesIfOneSocketCloseFails() throws IOException {
        UUID sessionId = UUID.randomUUID();

        WebSocketSession socket1 = mock(WebSocketSession.class);
        WebSocketSession socket2 = mock(WebSocketSession.class);

        when(socket1.isOpen()).thenReturn(true);
        when(socket2.isOpen()).thenReturn(true);

        doThrow(new IOException()).when(socket1).close(CloseStatus.POLICY_VIOLATION);

        registry.register(sessionId, socket1);
        registry.register(sessionId, socket2);

        registry.revoke(sessionId);

        verify(socket1).close(CloseStatus.POLICY_VIOLATION);
        verify(socket2).close(CloseStatus.POLICY_VIOLATION);

        assertThat(innerSessionMap).doesNotContainKey(sessionId);
    }

    @Test
    void revoke_isIdempotent() throws IOException {
        UUID sessionId = UUID.randomUUID();
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.isOpen()).thenReturn(true);

        registry.register(sessionId, socket);
        registry.revoke(sessionId);
        registry.revoke(sessionId);
        registry.revoke(sessionId);

        verify(socket, times(1)).close(CloseStatus.POLICY_VIOLATION);
        assertThat(innerSessionMap).doesNotContainKey(sessionId);
    }

    @Test
    void register_afterRevoke_createsFreshEntry() {
        UUID sessionId = UUID.randomUUID();

        WebSocketSession oldSocket = mock(WebSocketSession.class);
        WebSocketSession newSocket = mock(WebSocketSession.class);

        registry.register(sessionId, oldSocket);
        registry.revoke(sessionId);
        registry.register(sessionId, newSocket);

        assertThat(innerSessionMap).containsKey(sessionId);
        assertThat(innerSessionMap.get(sessionId)).containsExactly(newSocket);
    }

    @Test
    void revoke_skipsAlreadyClosedSockets() throws Exception {
        UUID sessionId = UUID.randomUUID();
        WebSocketSession socket = mock(WebSocketSession.class);

        when(socket.isOpen()).thenReturn(false);

        registry.register(sessionId, socket);
        registry.revoke(sessionId);

        verify(socket, never()).close(any());
        assertThat(innerSessionMap).doesNotContainKey(sessionId);
    }

    @Test
    void concurrentRegister_sameSession_doesNotLoseSockets() {

    }

    @Test
    void concurrentRegister_differentSessions_remainsIsolated() {

    }

    @Test
    void concurrentRegisterAndUnregister_doesNotCorruptRegistry() {

    }

    @Test
    void concurrentRevokeAndRegister_hasConsistentOutcome() {

    }
}
