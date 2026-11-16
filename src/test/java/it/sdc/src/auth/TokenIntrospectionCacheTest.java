package it.sdc.src.auth;

import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static it.sdc.src.test.fixtures.BearerAuthFixtures.*;
import static it.sdc.src.test.fixtures.UserFixtures.mockUserWithId;
import static it.sdc.src.utils.HashingUtils.sha512;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TokenIntrospectionCacheTest {
    private UserSessionDBRepository userSessionRepository;
    private PasswordEncoder passwordEncoder;
    private RedisTemplate<String, UserPrincipal> redis;
    private ValueOperations<String, UserPrincipal> values;

    private static final Base64.Encoder ENCODER = Base64.getEncoder();

    private TokenIntrospectionCache cache;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        userSessionRepository = mock(UserSessionDBRepository.class);

        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(any())).thenReturn("hash");

        redis = mock(RedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);

        cache = new TokenIntrospectionCache(userSessionRepository, redis);
    }

    private static String accessKey(SessionFixture fixture) {
        return "src:auth:access:"
                + ENCODER.encodeToString(sha512(fixture.plainAccessToken()));
    }

    private static String refreshKey(SessionFixture fixture) {
        return "src:auth:refresh:"
                + ENCODER.encodeToString(sha512(fixture.plainRefreshToken()));
    }

    private static boolean principalRefersToUser(UserPrincipal principal, UserSessionDB userSession) {
        if (principal == null || userSession == null) return false;
        return principal.getUserId().equals(userSession.getUser().getId()) &&
                principal.getSessionId().equals(userSession.getId()) &&
                principal.getUsername().equals(userSession.getUser().getUsername()) &&
                principal.getName().equals(userSession.getUser().getId().toString());
    }

    @Test
    void introspectAccessToken_shouldReturnValidUserPrincipalOnValidAccessToken() {
        SessionFixture fixture = mockSessionFixture(mockUserWithId(passwordEncoder));
        when(userSessionRepository.findByAccessToken(sha512(fixture.plainAccessToken())))
                .thenReturn(Optional.of(fixture.session()));

        UserPrincipal userPrincipal = cache.introspectAccessToken(ENCODER.encodeToString(fixture.plainAccessToken()));
        assertThat(principalRefersToUser(userPrincipal, fixture.session())).isTrue();
    }

    @Test
    void introspectAccessToken_shouldPopulateRedisOnMiss() {
        SessionFixture fixture = mockSessionFixture(mockUserWithId(passwordEncoder));
        byte[] hash = sha512(fixture.plainAccessToken());
        String key = accessKey(fixture);

        when(values.get(key)).thenReturn(null);
        when(userSessionRepository.findByAccessToken(hash))
                .thenReturn(Optional.of(fixture.session()));

        UserPrincipal result = cache.introspectAccessToken(
                ENCODER.encodeToString(fixture.plainAccessToken())
        );

        assertThat(principalRefersToUser(result, fixture.session())).isTrue();
        verify(userSessionRepository).findByAccessToken(hash);
        verify(values).set(eq(key), same(result), eq(Duration.ofMinutes(5)));
    }

    @Test
    void introspectAccessToken_shouldReturnCachedPrincipalWithoutQueryingDb() {
        SessionFixture fixture = mockSessionFixture(mockUserWithId(passwordEncoder));
        UserPrincipal principal = mockPrincipal(fixture.session());
        String key = accessKey(fixture);

        when(values.get(key)).thenReturn(principal);

        UserPrincipal result = cache.introspectAccessToken(
                ENCODER.encodeToString(fixture.plainAccessToken())
        );

        assertThat(result).isSameAs(principal);
        verifyNoInteractions(userSessionRepository);
        verify(values).get(key);
        verifyNoMoreInteractions(values);
    }

    @Test
    void introspectAccessToken_shouldRejectBadTokens() {
        byte[] random = "random".getBytes();
        byte[] randomHash = sha512(random);

        when(userSessionRepository.findByAccessToken(randomHash)).thenReturn(Optional.empty());
        assertThatThrownBy(
                () -> cache.introspectAccessToken(ENCODER.encodeToString(random))
        ).isInstanceOf(BadOpaqueTokenException.class);
        verify(values, never()).set(anyString(), any(UserPrincipal.class), any(Duration.class));
    }

    @Test
    void introspectAccessToken_shouldRejectAndEvictExpiredCachedToken() {
        SessionFixture fixture = mockSessionFixtureWithExpiredAccessToken(mockUserWithId(passwordEncoder));

        String key = accessKey(fixture);
        when(values.get(key)).thenReturn(mockPrincipal(fixture.session()));

        assertThatThrownBy(() -> cache.introspectAccessToken(
                ENCODER.encodeToString(fixture.plainAccessToken())
        )).isInstanceOf(BadOpaqueTokenException.class);

        verify(redis).delete(key);
        verifyNoInteractions(userSessionRepository);
        verify(values, never()).set(anyString(), any(UserPrincipal.class), any(Duration.class));
    }

    @Test
    void evict_shouldDeleteBothTokenKeys() {
        SessionFixture fixture = mockSessionFixture(mockUserWithId(passwordEncoder));

        cache.evict(fixture.session());

        verify(redis).delete(argThat((java.util.Collection<String> keys) ->
                keys.size() == 2 &&
                        keys.contains(accessKey(fixture)) &&
                        keys.contains(refreshKey(fixture))
        ));
        verifyNoInteractions(values, userSessionRepository);
    }

    @Test
    void introspectAccessToken_shouldRejectExpiredTokensAtDbLevel() {
        SessionFixture fixture = mockSessionFixtureWithExpiredAccessToken(mockUserWithId(passwordEncoder));
        when(userSessionRepository.findByAccessToken(sha512(fixture.plainAccessToken())))
                .thenReturn(Optional.of(fixture.session()));

        assertThatThrownBy(
                () -> cache.introspectAccessToken(ENCODER.encodeToString(fixture.plainAccessToken()))
        ).isInstanceOf(BadOpaqueTokenException.class);
        verify(values, never()).set(anyString(), any(UserPrincipal.class), any(Duration.class));
    }

    @Test
    void introspectAccessToken_shouldRejectMalformedTokens() {
        assertThatThrownBy(
                () -> cache.introspectAccessToken("hello")
        ).isInstanceOf(BadOpaqueTokenException.class);
        verify(values, never()).set(anyString(), any(UserPrincipal.class), any(Duration.class));
    }

    @Test
    void introspectRefreshToken_shouldPopulateRedisOnMiss() {
        SessionFixture fixture = mockSessionFixture(mockUserWithId(passwordEncoder));
        byte[] hash = sha512(fixture.plainRefreshToken());
        String key = refreshKey(fixture);

        when(values.get(key)).thenReturn(null);
        when(userSessionRepository.findByRefreshToken(hash))
                .thenReturn(Optional.of(fixture.session()));

        UserPrincipal result = cache.introspectRefreshToken(
                ENCODER.encodeToString(fixture.plainRefreshToken())
        );

        assertThat(principalRefersToUser(result, fixture.session())).isTrue();
        verify(userSessionRepository).findByRefreshToken(hash);
        verify(values).set(eq(key), same(result), eq(Duration.ofMinutes(15)));
    }

    @Test
    void introspectRefreshToken_shouldReturnCachedPrincipalWithoutQueryingDb() {
        SessionFixture fixture = mockSessionFixture(mockUserWithId(passwordEncoder));
        UserPrincipal principal = mockPrincipal(fixture.session());
        String key = refreshKey(fixture);

        when(values.get(key)).thenReturn(principal);

        UserPrincipal result = cache.introspectRefreshToken(
                ENCODER.encodeToString(fixture.plainRefreshToken())
        );

        assertThat(result).isSameAs(principal);
        verifyNoInteractions(userSessionRepository);
        verify(values).get(key);
        verifyNoMoreInteractions(values);
    }

    @Test
    void introspectRefreshToken_shouldReturnValidUserPrincipalOnValidRefreshToken() {
        SessionFixture fixture = mockSessionFixture(mockUserWithId(passwordEncoder));
        when(userSessionRepository.findByRefreshToken(sha512(fixture.plainRefreshToken())))
                .thenReturn(Optional.of(fixture.session()));

        UserPrincipal userPrincipal = cache.introspectRefreshToken(ENCODER.encodeToString(fixture.plainRefreshToken()));
        assertThat(principalRefersToUser(userPrincipal, fixture.session())).isTrue();
    }

    @Test
    void introspectRefreshToken_shouldRejectBadTokens() {
        byte[] random = "random".getBytes();
        byte[] randomHash = sha512(random);

        when(userSessionRepository.findByRefreshToken(randomHash)).thenReturn(Optional.empty());
        assertThatThrownBy(
                () -> cache.introspectRefreshToken(ENCODER.encodeToString(random))
        ).isInstanceOf(BadOpaqueTokenException.class);
        verify(values, never()).set(anyString(), any(UserPrincipal.class), any(Duration.class));
    }

    @Test
    void introspectRefreshToken_shouldRejectAndEvictExpiredCachedToken() {
        SessionFixture fixture = mockSessionFixtureWithExpiredRefreshToken(mockUserWithId(passwordEncoder));

        String key = refreshKey(fixture);
        when(values.get(key)).thenReturn(mockPrincipal(fixture.session()));

        assertThatThrownBy(() -> cache.introspectRefreshToken(
                ENCODER.encodeToString(fixture.plainRefreshToken())
        )).isInstanceOf(BadOpaqueTokenException.class);

        verify(redis).delete(key);
        verifyNoInteractions(userSessionRepository);
        verify(values, never()).set(anyString(), any(UserPrincipal.class), any(Duration.class));
    }

    @Test
    void introspectRefreshToken_shouldRejectExpiredTokensAtDbLevel() {
        SessionFixture fixture = mockSessionFixtureWithExpiredRefreshToken(mockUserWithId(passwordEncoder));
        when(userSessionRepository.findByRefreshToken(sha512(fixture.plainRefreshToken())))
                .thenReturn(Optional.of(fixture.session()));

        assertThatThrownBy(
                () -> cache.introspectRefreshToken(ENCODER.encodeToString(fixture.plainRefreshToken()))
        ).isInstanceOf(BadOpaqueTokenException.class);
    }

    @Test
    void introspectRefreshToken_shouldRejectMalformedTokens() {
        assertThatThrownBy(
                () -> cache.introspectRefreshToken("hello")
        ).isInstanceOf(BadOpaqueTokenException.class);
    }

    @Test
    void evictAll_shouldDeleteBothKeysForEverySession() {
        SessionFixture first = mockSessionFixture(mockUserWithId(passwordEncoder));
        SessionFixture second = mockSessionFixture(mockUserWithId(passwordEncoder));

        cache.evictAll(List.of(first.session(), second.session()));

        verify(redis).delete(argThat((java.util.Collection<String> keys) ->
                keys.size() == 4 && keys.containsAll(List.of(
                        accessKey(first),
                        refreshKey(first),
                        accessKey(second),
                        refreshKey(second)
                ))
        ));
        verifyNoInteractions(values, userSessionRepository);
    }

    @Test
    void evictAll_shouldDoNothingForEmptyCollection() {
        cache.evictAll(List.of());
        verifyNoInteractions(redis, values, userSessionRepository);
    }
}
