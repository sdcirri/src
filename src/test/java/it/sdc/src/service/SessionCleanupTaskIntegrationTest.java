package it.sdc.src.service;

import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import it.sdc.src.ws.WebSocketSessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketSession;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static it.sdc.src.test.fixtures.BearerAuthFixtures.*;
import static it.sdc.src.test.fixtures.UserFixtures.mockUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
public class SessionCleanupTaskIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.1");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    UserDBRepository userRepository;

    @Autowired
    UserSessionDBRepository sessionRepository;

    @Autowired
    SessionCleanupTask sessionCleanupTask;

    @Autowired
    WebSocketSessionRegistry webSocketSessionRegistry;

    @Autowired
    PasswordEncoder passwordEncoder;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
        sessionRepository.deleteAll();
    }

    @Test
    void cleanupSessions_shouldDeleteOldSessionsOnly() {
        UserDB user = userRepository.save(mockUser(passwordEncoder));
        UserSessionDB expired = sessionRepository.save(mockSessionWithExpiredRefreshToken(user));
        UserSessionDB active = sessionRepository.save(mockSession(user));
        UserSessionDB accessExpired = sessionRepository.save(mockSessionWithExpiredAccessToken(user));

        assertThat(sessionRepository.count()).isEqualTo(3);

        sessionCleanupTask.cleanupSessions();

        assertThat(sessionRepository.findById(expired.getId())).isEmpty();
        assertThat(sessionRepository.findById(active.getId())).isPresent();
        // access expired + refresh still valid is still recoverable and should stay
        assertThat(sessionRepository.findById(accessExpired.getId())).isPresent();
    }


    @Test
    void cleanupSessions_shouldRevokeWebsocketsFromRegistry() {
        UserDB user = userRepository.save(mockUser(passwordEncoder));
        List<UUID> expiredSessionIds = List.of(
                sessionRepository.save(mockSessionWithExpiredRefreshToken(user)).getId(),
                sessionRepository.save(mockSessionWithExpiredRefreshToken(user)).getId(),
                sessionRepository.save(mockSessionWithExpiredRefreshToken(user)).getId()
        );

        for (UUID sessionId : expiredSessionIds)
            webSocketSessionRegistry.register(sessionId, mock(WebSocketSession.class));

        @SuppressWarnings("unchecked")
        Map<UUID, Set<WebSocketSession>> innerSessionMap = (Map<UUID, Set<WebSocketSession>>)
                ReflectionTestUtils.getField(webSocketSessionRegistry, "sessions");
        assertThat(innerSessionMap).isNotNull();
        assertThat(innerSessionMap.keySet()).containsExactlyInAnyOrderElementsOf(expiredSessionIds);

        sessionCleanupTask.cleanupSessions();

        for (UUID sessionId : expiredSessionIds)
            assertThat(sessionRepository.findById(sessionId)).isEmpty();
        assertThat(innerSessionMap.keySet()).doesNotContainAnyElementsOf(expiredSessionIds);
    }
}
