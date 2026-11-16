package it.sdc.src.auth;

import it.sdc.src.config.RedisConfig;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static it.sdc.src.test.fixtures.BearerAuthFixtures.*;
import static it.sdc.src.test.fixtures.UserFixtures.mockUserWithId;
import static it.sdc.src.utils.HashingUtils.sha512;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataRedisTest
@Import({TokenIntrospectionCache.class, RedisConfig.class})
@Testcontainers
class TokenIntrospectionCacheRedisTest {

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8-alpine")
    ).withExposedPorts(6379);

    @Autowired
    private TokenIntrospectionCache cache;

    @Autowired
    private RedisTemplate<String, UserPrincipal> redis;

    @MockitoBean
    private UserSessionDBRepository sessionRepository;

    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        try (var connection = redis.getRequiredConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(any())).thenReturn("hash");
    }

    private SessionFixture fixture() {
        return mockSessionFixture(mockUserWithId(passwordEncoder));
    }

    private static String bearer(SessionFixture fixture, boolean refresh) {
        byte[] plain = refresh ? fixture.plainRefreshToken() : fixture.plainAccessToken();
        return Base64.getEncoder().encodeToString(plain);
    }

    private static String key(SessionFixture fixture, boolean refresh) {
        byte[] plain = refresh ? fixture.plainRefreshToken() : fixture.plainAccessToken();
        String prefix = refresh ? "src:auth:refresh:" : "src:auth:access:";

        return prefix + Base64.getEncoder().encodeToString(sha512(plain));
    }

    private void stubSession(SessionFixture fixture) {
        when(sessionRepository.findByAccessToken(
                sha512(fixture.plainAccessToken())
        )).thenReturn(Optional.of(fixture.session()));

        when(sessionRepository.findByRefreshToken(
                sha512(fixture.plainRefreshToken())
        )).thenReturn(Optional.of(fixture.session()));
    }

    private UserPrincipal introspect(
            SessionFixture fixture,
            boolean refresh
    ) {
        return refresh
                ? cache.introspectRefreshToken(bearer(fixture, true))
                : cache.introspectAccessToken(bearer(fixture, false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRoundTripPrincipalThroughRedis(boolean refresh) {
        SessionFixture fixture = fixture();
        stubSession(fixture);

        // Miss: repository -> principal -> JSON in Redis.
        UserPrincipal first = introspect(fixture, refresh);

        assertThat(redis.hasKey(key(fixture, refresh))).isTrue();

        clearInvocations(sessionRepository);

        // Hit: JSON in Redis -> new principal.
        UserPrincipal second = introspect(fixture, refresh);

        assertThat(second).isNotSameAs(first);
        assertThat(second)
                .usingRecursiveComparison()
                .isEqualTo(first);

        assertThat(second.getUserId()).isEqualTo(fixture.session().getUser().getId());
        assertThat(second.getSessionId()).isEqualTo(fixture.session().getId());

        verifyNoInteractions(sessionRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldSetExpectedTtl(boolean refresh) {
        SessionFixture fixture = fixture();
        stubSession(fixture);

        introspect(fixture, refresh);

        Long ttl = redis.getExpire(key(fixture, refresh), TimeUnit.SECONDS);
        long expected = refresh ? 900L : 300L;

        assertThat(ttl).isNotNull();
        assertThat(ttl).isBetween(expected - 30L, expected);
    }

    @Test
    void evict_shouldDeleteBothKeysAndForceDatabaseReload() {
        SessionFixture fixture = fixture();
        stubSession(fixture);

        introspect(fixture, false);
        introspect(fixture, true);

        assertThat(redis.hasKey(key(fixture, false))).isTrue();
        assertThat(redis.hasKey(key(fixture, true))).isTrue();

        clearInvocations(sessionRepository);

        cache.evict(fixture.session());

        assertThat(redis.hasKey(key(fixture, false))).isFalse();
        assertThat(redis.hasKey(key(fixture, true))).isFalse();

        introspect(fixture, false);
        introspect(fixture, true);

        verify(sessionRepository).findByAccessToken(
                sha512(fixture.plainAccessToken())
        );
        verify(sessionRepository).findByRefreshToken(
                sha512(fixture.plainRefreshToken())
        );
        verifyNoMoreInteractions(sessionRepository);
    }

    @Test
    void evictAll_shouldDeleteSelectedSessionsAndPreserveOthers() {
        SessionFixture first = fixture();
        SessionFixture second = fixture();
        SessionFixture untouched = fixture();

        for (SessionFixture fixture : List.of(first, second, untouched)) {
            stubSession(fixture);
            introspect(fixture, false);
            introspect(fixture, true);
        }

        cache.evictAll(List.of(first.session(), second.session()));

        for (SessionFixture fixture : List.of(first, second)) {
            assertThat(redis.hasKey(key(fixture, false))).isFalse();
            assertThat(redis.hasKey(key(fixture, true))).isFalse();
        }

        assertThat(redis.hasKey(key(untouched, false))).isTrue();
        assertThat(redis.hasKey(key(untouched, true))).isTrue();

        clearInvocations(sessionRepository);

        introspect(untouched, false);
        introspect(untouched, true);

        verifyNoInteractions(sessionRepository);
    }
}
