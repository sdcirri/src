package it.sdc.src.service;

import it.sdc.src.db.repositories.UserSessionDBRepository;
import it.sdc.src.ws.WebSocketSessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

public class SessionCleanupTaskTest {
    private UserSessionDBRepository sessionRepository;

    private SessionCleanupTask sessionCleanupTask;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();

        sessionRepository = mock(UserSessionDBRepository.class);
        sessionCleanupTask = new SessionCleanupTask(mock(WebSocketSessionRegistry.class), sessionRepository);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void cleanupSessions_shouldDeleteOldSessions() {
        UUID expiredSessionId = UUID.randomUUID();
        when(sessionRepository.findExpiredSessionIds()).thenReturn(List.of(expiredSessionId));

        sessionCleanupTask.cleanupSessions();
        verify(sessionRepository).clearExpiredSessions();
    }

    @Test
    void cleanupSessions_shouldSkipQueryWhenNoExpiredSessions() {
        when(sessionRepository.findExpiredSessionIds()).thenReturn(List.of());
        sessionCleanupTask.cleanupSessions();
        verify(sessionRepository, never()).clearExpiredSessions();
    }
}
