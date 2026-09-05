package it.sdc.src.auth;

import com.github.benmanes.caffeine.cache.Cache;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private MessageDigest sha512;

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
    void setUp() throws NoSuchAlgorithmException {
        userSessionRepository = mock(UserSessionDBRepository.class);
        sha512 = MessageDigest.getInstance("SHA-512");

        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(any())).thenReturn("hash");

        cache = new TokenIntrospectionCache(userSessionRepository, sha512);
    }

    private static boolean principalRefersToUser(UserPrincipal principal, UserSessionDB userSession) {
        if (principal == null || userSession == null) return false;
        return principal.getUserId().equals(userSession.getUser().getId()) &&
                principal.getSessionId().equals(userSession.getId()) &&
                principal.getUsername().equals(userSession.getUser().getUsername()) &&
                principal.getName().equals(userSession.getUser().getUsername());
    }

    @Test
    void introspectAccessToken_shouldReturnValidUserPrincipalOnValidAccessToken() {
        SessionFixture fixture = mockSessionFixture(mockUserWithId(passwordEncoder));
        when(userSessionRepository.findByAccessToken(sha512.digest(fixture.plainAccessToken())))
                .thenReturn(Optional.of(fixture.session()));

        UserPrincipal userPrincipal = cache.introspectAccessToken(ENCODER.encodeToString(fixture.plainAccessToken()));
        assertThat(principalRefersToUser(userPrincipal, fixture.session())).isTrue();
    }

    @Test
    void introspectAccessToken_shouldCacheTokens() {
        SessionFixture fixture = mockSessionFixture(mockUserWithId(passwordEncoder));
        byte[] accessTokenHash = sha512.digest(fixture.plainAccessToken());
        when(userSessionRepository.findByAccessToken(accessTokenHash)).thenReturn(Optional.of(fixture.session()));
        String accessToken = ENCODER.encodeToString(fixture.plainAccessToken());

        cache.introspectAccessToken(accessToken);
        verify(userSessionRepository, times(1)).findByAccessToken(accessTokenHash);
        cache.introspectAccessToken(accessToken);
        verify(userSessionRepository, times(1)).findByAccessToken(accessTokenHash);
    }

    @Test
    void introspectAccessToken_shouldRejectBadTokens() {
        byte[] random = "random".getBytes();
        byte[] randomHash = sha512.digest(random);

        when(userSessionRepository.findByAccessToken(randomHash)).thenReturn(Optional.empty());
        assertThatThrownBy(
                () -> cache.introspectAccessToken(ENCODER.encodeToString(random))
        ).isInstanceOf(BadOpaqueTokenException.class);
    }

    @Test
    void introspectAccessToken_shouldRejectExpiredTokens() throws NoSuchFieldException, IllegalAccessException {
        SessionFixture fixture = mockSessionFixtureWithExpiredAccessToken(mockUserWithId(passwordEncoder));
        String token = ENCODER.encodeToString(fixture.plainAccessToken());
        seedCache("accessCache", token, mockPrincipal(fixture.session()));

        assertThatThrownBy(
                () -> cache.introspectAccessToken(token)
        ).isInstanceOf(BadOpaqueTokenException.class);
        verifyNoInteractions(userSessionRepository);
    }

    @Test
    void introspectAccessToken_shouldRejectExpiredTokensAtDbLevel() {
        SessionFixture fixture = mockSessionFixtureWithExpiredAccessToken(mockUserWithId(passwordEncoder));
        when(userSessionRepository.findByAccessToken(sha512.digest(fixture.plainAccessToken())))
                .thenReturn(Optional.of(fixture.session()));

        assertThatThrownBy(
                () -> cache.introspectAccessToken(ENCODER.encodeToString(fixture.plainAccessToken()))
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
        SessionFixture fixture = mockSessionFixture(mockUserWithId(passwordEncoder));
        when(userSessionRepository.findByRefreshToken(sha512.digest(fixture.plainRefreshToken())))
                .thenReturn(Optional.of(fixture.session()));

        UserPrincipal userPrincipal = cache.introspectRefreshToken(ENCODER.encodeToString(fixture.plainRefreshToken()));
        assertThat(principalRefersToUser(userPrincipal, fixture.session())).isTrue();
    }

    @Test
    void introspectRefreshToken_shouldCacheTokens() {
        SessionFixture fixture = mockSessionFixture(mockUserWithId(passwordEncoder));
        byte[] refreshTokenHash = sha512.digest(fixture.plainRefreshToken());
        when(userSessionRepository.findByRefreshToken(refreshTokenHash)).thenReturn(Optional.of(fixture.session()));
        String refreshToken = ENCODER.encodeToString(fixture.plainRefreshToken());

        cache.introspectRefreshToken(refreshToken);
        verify(userSessionRepository, times(1)).findByRefreshToken(refreshTokenHash);
        cache.introspectRefreshToken(refreshToken);
        verify(userSessionRepository, times(1)).findByRefreshToken(refreshTokenHash);
    }

    @Test
    void introspectRefreshToken_shouldRejectBadTokens() {
        byte[] random = "random".getBytes();
        byte[] randomHash = sha512.digest(random);

        when(userSessionRepository.findByRefreshToken(randomHash)).thenReturn(Optional.empty());
        assertThatThrownBy(
                () -> cache.introspectRefreshToken(ENCODER.encodeToString(random))
        ).isInstanceOf(BadOpaqueTokenException.class);
    }

    @Test
    void introspectRefreshToken_shouldRejectExpiredTokens() throws NoSuchFieldException, IllegalAccessException {
        SessionFixture fixture = mockSessionFixtureWithExpiredRefreshToken(mockUserWithId(passwordEncoder));
        String token = ENCODER.encodeToString(fixture.plainRefreshToken());
        seedCache("refreshCache", token, mockPrincipal(fixture.session()));

        assertThatThrownBy(
                () -> cache.introspectRefreshToken(token)
        ).isInstanceOf(BadOpaqueTokenException.class);
        verifyNoInteractions(userSessionRepository);
    }

    @Test
    void introspectRefreshToken_shouldRejectExpiredTokensAtDbLevel() {
        SessionFixture fixture = mockSessionFixtureWithExpiredRefreshToken(mockUserWithId(passwordEncoder));
        when(userSessionRepository.findByRefreshToken(sha512.digest(fixture.plainRefreshToken())))
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
}
