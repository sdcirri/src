package it.sdc.src.service;

import it.sdc.src.db.repositories.UserSessionDBRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class SessionCleanupTask {
    private final UserSessionDBRepository sessionRepository;

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void cleanupSessions() {
        sessionRepository.clearExpiredSessions();
    }
}
