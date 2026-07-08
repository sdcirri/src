package it.sdc.src.auth;

import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import static it.sdc.src.test.fixtures.BearerAuthFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TokenIntrospectionCacheTest {
    private UserSessionDBRepository userSessionRepository;
    private PasswordEncoder passwordEncoder;

    private static final Base64.Encoder ENCODER = Base64.getEncoder();

    private TokenIntrospectionCache cache;

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
    void introspectAccessToken_shouldRejectExpiredTokens() {
        UserSessionDB userSession = mockSessionWithExpiredAccessToken(mockUserWithId(passwordEncoder));
        when(userSessionRepository.findByAccessToken(userSession.getAccessToken())).thenReturn(Optional.of(userSession));

        assertThatThrownBy(
                () -> cache.introspectAccessToken(ENCODER.encodeToString(userSession.getAccessToken()))
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
    void introspectRefreshToken_shouldRejectExpiredTokens() {
        UserSessionDB userSession = mockSessionWithExpiredRefreshToken(mockUserWithId(passwordEncoder));
        when(userSessionRepository.findByRefreshToken(userSession.getRefreshToken())).thenReturn(Optional.of(userSession));

        assertThatThrownBy(
                () -> cache.introspectRefreshToken(ENCODER.encodeToString(userSession.getRefreshToken()))
        ).isInstanceOf(BadOpaqueTokenException.class);
    }
}
