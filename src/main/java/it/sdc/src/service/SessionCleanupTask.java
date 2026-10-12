package it.sdc.src.service;

import it.sdc.src.db.repositories.UserSessionDBRepository;
import it.sdc.src.ws.WebSocketSessionRegistry;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class SessionCleanupTask {
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final UserSessionDBRepository sessionRepository;

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void cleanupSessions() {
        List<UUID> expiredSessions = sessionRepository.findExpiredSessionIds();
        if (expiredSessions.isEmpty()) return;

        // the sessions in expiredSessions are gone for good, as "refresh"
        //   yields a new session and deletes the old one (see AuthService.refreshAccessToken)
        sessionRepository.clearExpiredSessions();

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        expiredSessions.forEach(webSocketSessionRegistry::revoke);
                    }
                }
        );
    }
}
