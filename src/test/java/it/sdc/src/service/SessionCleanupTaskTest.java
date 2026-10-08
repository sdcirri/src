package it.sdc.src.service;

import it.sdc.src.db.repositories.UserSessionDBRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SessionCleanupTaskTest {
    private UserSessionDBRepository sessionRepository;

    private SessionCleanupTask sessionCleanupTask;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(UserSessionDBRepository.class);
        sessionCleanupTask = new SessionCleanupTask(sessionRepository);
    }

    @Test
    void cleanupSessions_shouldDeleteOldSessions() {
        sessionCleanupTask.cleanupSessions();
        verify(sessionRepository).clearExpiredSessions();
    }
}
