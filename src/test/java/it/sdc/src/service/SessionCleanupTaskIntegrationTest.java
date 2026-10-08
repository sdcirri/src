package it.sdc.src.service;

import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static it.sdc.src.test.fixtures.BearerAuthFixtures.*;
import static it.sdc.src.test.fixtures.UserFixtures.mockUser;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
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
    PasswordEncoder passwordEncoder;

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
}
