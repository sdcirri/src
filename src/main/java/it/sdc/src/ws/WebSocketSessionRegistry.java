package it.sdc.src.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class WebSocketSessionRegistry {
    private final Map<UUID, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(UUID authSessionId, WebSocketSession socket) {
        sessions.computeIfAbsent(
                authSessionId,
                ignored -> ConcurrentHashMap.newKeySet()
        ).add(socket);
    }

    public void unregister(UUID authSessionId, WebSocketSession socket) {
        Set<WebSocketSession> sockets = sessions.get(authSessionId);
        if (sockets == null) return;

        sockets.remove(socket);

        if (sockets.isEmpty())
            sessions.remove(authSessionId, sockets);
    }

    public void revoke(UUID authSessionId) {
        Set<WebSocketSession> sockets = sessions.remove(authSessionId);
        if (sockets == null) return;

        for (WebSocketSession socket : sockets) {
            try {
                if (socket.isOpen())
                    socket.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException e) {
                log.warn("Failed to close websocket {}", socket.getId(), e);
            }
        }
    }
}
