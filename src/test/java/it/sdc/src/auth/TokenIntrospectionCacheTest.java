package it.sdc.src.auth;

import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import static it.sdc.src.test.fixtures.BearerAuthFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
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
        String accessToken = ENCODER.encodeToString(userSession.getAccessToken());

        UserPrincipal userPrincipal = cache.introspectAccessToken(accessToken);
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

    }

    @Test
    void introspectAccessToken_shouldRejectExpiredTokens() {

    }

    @Test
    void introspectRefreshToken_shouldReturnValidUserPrincipalOnValidAccessToken() {
        UserSessionDB userSession = mockSession(mockUserWithId(passwordEncoder));
        when(userSessionRepository.findByAccessToken(userSession.getAccessToken())).thenReturn(Optional.of(userSession));
        String accessToken = ENCODER.encodeToString(userSession.getAccessToken());

        UserPrincipal userPrincipal = cache.introspectAccessToken(accessToken);
        assertThat(principalRefersToUser(userPrincipal, userSession)).isTrue();
    }

    @Test
    void introspectRefreshToken_shouldCacheTokens() {
        UserSessionDB userSession = mockSession(mockUserWithId(passwordEncoder));
        when(userSessionRepository.findByAccessToken(userSession.getAccessToken())).thenReturn(Optional.of(userSession));
        String accessToken = ENCODER.encodeToString(userSession.getAccessToken());

        cache.introspectAccessToken(accessToken);
        verify(userSessionRepository, times(1)).findByAccessToken(userSession.getAccessToken());
        cache.introspectAccessToken(accessToken);
        verify(userSessionRepository, times(1)).findByAccessToken(userSession.getAccessToken());
    }

    @Test
    void introspectRefreshToken_shouldRejectBadTokens() {

    }

    @Test
    void introspectRefreshToken_shouldRejectExpiredTokens() {

    }
}
