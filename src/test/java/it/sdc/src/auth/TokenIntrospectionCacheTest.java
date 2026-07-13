package it.sdc.src.auth;

import com.github.benmanes.caffeine.cache.Cache;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.Optional;

import static it.sdc.src.test.fixtures.BearerAuthFixtures.*;
import static it.sdc.src.test.fixtures.UserFixtures.mockUserWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TokenIntrospectionCacheTest {
    private UserSessionDBRepository userSessionRepository;
    private PasswordEncoder passwordEncoder;

    private static final Base64.Encoder ENCODER = Base64.getEncoder();

    private TokenIntrospectionCache cache;

    @SuppressWarnings("unchecked")
    private Cache<String, UserPrincipal> getCache(String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Field field = TokenIntrospectionCache.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Cache<String, UserPrincipal>) field.get(cache);
    }

    private void seedCache(String fieldName, String key, UserPrincipal principal) throws NoSuchFieldException, IllegalAccessException {
        getCache(fieldName).put(key, principal);
    }

    @BeforeEach
    void setUp() {
        userSessionRepository = mock(UserSessionDBRepository.class);

        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(any())).thenReturn("hash");

        cache = new TokenIntrospectionCache(userSessionRepository);
    }

    private static boolean principalRefersToUser(UserPrincipal principal, UserSessionDB userSession) {
        if (principal == null || userSession == null) return false;
        return principal.getUserId().equals(userSession.getUser().getId()) &&
                principal.getUsername().equals(userSession.getUser().getUsername()) &&
                principal.getName().equals(userSession.getUser().getUsername());
    }

    @Test
    void introspectAccessToken_shouldReturnValidUserPrincipalOnValidAccessToken() {
        UserSessionDB userSession = mockSession(mockUserWithId(passwordEncoder));
        when(userSessionRepository.findByAccessToken(userSession.getAccessToken())).thenReturn(Optional.of(userSession));

        UserPrincipal userPrincipal = cache.introspectAccessToken(ENCODER.encodeToString(userSession.getAccessToken()));
        assertThat(principalRefersToUser(userPrincipal, userSession)).isTrue();
    }

    @Test
    void introspectAccessToken_shouldCacheTokens() {
        UserSessionDB userSession = mockSession(mockUserWithId(passwordEncoder));
        when(userSessionRepository.findByAccessToken(userSession.getAccessToken())).thenReturn(Optional.of(userSession));
        String accessToken = ENCODER.encodeToString(userSession.getAccessToken());

        cache.introspectAccessToken(accessToken);
        verify(userSessionRepository, times(1)).findByAccessToken(userSession.getAccessToken());
        cache.introspectAccessToken(accessToken);
        verify(userSessionRepository, times(1)).findByAccessToken(userSession.getAccessToken());
    }

    @Test
    void introspectAccessToken_shouldRejectBadTokens() {
        byte[] random = "random".getBytes();

        when(userSessionRepository.findByAccessToken(random)).thenReturn(Optional.empty());
        assertThatThrownBy(
                () -> cache.introspectAccessToken(ENCODER.encodeToString(random))
        ).isInstanceOf(BadOpaqueTokenException.class);
    }

    @Test
    void introspectAccessToken_shouldRejectExpiredTokens() throws NoSuchFieldException, IllegalAccessException {
        UserSessionDB userSession = mockSessionWithExpiredAccessToken(mockUserWithId(passwordEncoder));
        String token = ENCODER.encodeToString(userSession.getAccessToken());
        seedCache("accessCache", token, mockPrincipal(userSession));

        assertThatThrownBy(
                () -> cache.introspectAccessToken(token)
        ).isInstanceOf(BadOpaqueTokenException.class);
        verifyNoInteractions(userSessionRepository);
    }

    @Test
    void introspectAccessToken_shouldRejectExpiredTokensAtDbLevel() {
        UserSessionDB userSession = mockSessionWithExpiredAccessToken(mockUserWithId(passwordEncoder));
        when(userSessionRepository.findByAccessToken(userSession.getAccessToken())).thenReturn(Optional.of(userSession));

        assertThatThrownBy(
                () -> cache.introspectAccessToken(ENCODER.encodeToString(userSession.getAccessToken()))
        ).isInstanceOf(BadOpaqueTokenException.class);
    }

    @Test
    void introspectAccessToken_shouldRejectMalformedTokens() {
        assertThatThrownBy(
                () -> cache.introspectAccessToken("hello")
        ).isInstanceOf(BadOpaqueTokenException.class);
    }

    @Test
    void introspectRefreshToken_shouldReturnValidUserPrincipalOnValidRefreshToken() {
        UserSessionDB userSession = mockSession(mockUserWithId(passwordEncoder));
        when(userSessionRepository.findByRefreshToken(userSession.getRefreshToken())).thenReturn(Optional.of(userSession));

        UserPrincipal userPrincipal = cache.introspectRefreshToken(ENCODER.encodeToString(userSession.getRefreshToken()));
        assertThat(principalRefersToUser(userPrincipal, userSession)).isTrue();
    }

    @Test
    void introspectRefreshToken_shouldCacheTokens() {
        UserSessionDB userSession = mockSession(mockUserWithId(passwordEncoder));
        when(userSessionRepository.findByRefreshToken(userSession.getRefreshToken())).thenReturn(Optional.of(userSession));
        String refreshToken = ENCODER.encodeToString(userSession.getRefreshToken());

        cache.introspectRefreshToken(refreshToken);
        verify(userSessionRepository, times(1)).findByRefreshToken(userSession.getRefreshToken());
        cache.introspectRefreshToken(refreshToken);
        verify(userSessionRepository, times(1)).findByRefreshToken(userSession.getRefreshToken());
    }

    @Test
    void introspectRefreshToken_shouldRejectBadTokens() {
        byte[] random = "random".getBytes();

        when(userSessionRepository.findByRefreshToken(random)).thenReturn(Optional.empty());
        assertThatThrownBy(
                () -> cache.introspectRefreshToken(ENCODER.encodeToString(random))
        ).isInstanceOf(BadOpaqueTokenException.class);
    }

    @Test
    void introspectRefreshToken_shouldRejectExpiredTokens() throws NoSuchFieldException, IllegalAccessException {
        UserSessionDB userSession = mockSessionWithExpiredRefreshToken(mockUserWithId(passwordEncoder));
        String token = ENCODER.encodeToString(userSession.getRefreshToken());
        seedCache("refreshCache", token, mockPrincipal(userSession));

        assertThatThrownBy(
                () -> cache.introspectRefreshToken(token)
        ).isInstanceOf(BadOpaqueTokenException.class);
        verifyNoInteractions(userSessionRepository);
    }

    @Test
    void introspectRefreshToken_shouldRejectExpiredTokensAtDbLevel() {
        UserSessionDB userSession = mockSessionWithExpiredRefreshToken(mockUserWithId(passwordEncoder));
        when(userSessionRepository.findByRefreshToken(userSession.getRefreshToken())).thenReturn(Optional.of(userSession));

        assertThatThrownBy(
                () -> cache.introspectRefreshToken(ENCODER.encodeToString(userSession.getRefreshToken()))
        ).isInstanceOf(BadOpaqueTokenException.class);
    }

    @Test
    void introspectRefreshToken_shouldRejectMalformedTokens() {
        assertThatThrownBy(
                () -> cache.introspectRefreshToken("hello")
        ).isInstanceOf(BadOpaqueTokenException.class);
    }
}
