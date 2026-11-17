package it.sdc.src.service;

import it.sdc.src.auth.TokenIntrospectionCache;
import it.sdc.src.config.AuthProperties;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import it.sdc.src.dto.UserSessionDto;
import it.sdc.src.exceptions.LoginFailedException;
import it.sdc.src.ws.WebSocketSessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

import static it.sdc.src.utils.HashingUtils.sha512;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

public class SessionServiceTest {
    private UserSessionDBRepository userSessionRepository;
    private WebSocketSessionRegistry webSocketSessionRegistry;
    private TokenIntrospectionCache tokenIntrospectionCache;

    private SessionService sessionService;

    private AuthProperties authProperties() {
        AuthProperties authProperties = new AuthProperties();
        authProperties.setAccessTokenValiditySeconds(3600);
        authProperties.setRefreshTokenValiditySeconds(3600);
        return authProperties;
    }

    private static boolean isBase64Token(String data) {
        try {
            return Base64.getDecoder().decode(data).length == 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @BeforeEach
    public void setUp() {
        userSessionRepository = mock(UserSessionDBRepository.class);
        webSocketSessionRegistry = mock(WebSocketSessionRegistry.class);
        tokenIntrospectionCache = mock(TokenIntrospectionCache.class);

        sessionService = new SessionService(
                userSessionRepository,
                webSocketSessionRegistry,
                tokenIntrospectionCache,
                new SecureRandom(),
                authProperties()
        );
    }

    @BeforeEach
    void setUpTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void yieldSession_shouldYieldSession() {
        UUID userId = UUID.randomUUID();
        UserDB user = mock(UserDB.class);
        when(user.getId()).thenReturn(userId);

        UserSessionDto sessionDto = sessionService.yieldSession(user);
        assertThat(sessionDto).isNotNull();
        assertThat(sessionDto.id()).isEqualTo(userId);
        assertThat(isBase64Token(sessionDto.accessToken())).isTrue();
        assertThat(sessionDto.accessTokenExpires()).isNotNull();
        assertThat(isBase64Token(sessionDto.refreshToken())).isTrue();
        assertThat(sessionDto.refreshTokenExpires()).isNotNull();
        assertThat(sessionDto.accessToken()).isNotEqualTo(sessionDto.refreshToken());

        ArgumentCaptor<UserSessionDB> captor = ArgumentCaptor.forClass(UserSessionDB.class);
        verify(userSessionRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getAccessToken()).isEqualTo(
                sha512(Base64.getDecoder().decode(sessionDto.accessToken()))
        );
        assertThat(captor.getValue().getAccessTokenExpires().toEpochMilli()).isEqualTo(sessionDto.accessTokenExpires());
        assertThat(captor.getValue().getRefreshToken()).isEqualTo(
                sha512(Base64.getDecoder().decode(sessionDto.refreshToken()))
        );
        assertThat(captor.getValue().getRefreshTokenExpires().toEpochMilli()).isEqualTo(sessionDto.refreshTokenExpires());
    }

    @Test
    void yieldSession_regeneratesTokensWhenAccessAndRefreshCollide() {
        SecureRandom collidingRandom = mock(SecureRandom.class);
        java.util.concurrent.atomic.AtomicInteger nextBytesCalls = new java.util.concurrent.atomic.AtomicInteger();

        doAnswer(invocation -> {
            byte[] buffer = invocation.getArgument(0);
            int call = nextBytesCalls.getAndIncrement();
            if (call < 2) {
                Arrays.fill(buffer, (byte) 7);
            } else if (call == 2) {
                Arrays.fill(buffer, (byte) 1);
            } else {
                Arrays.fill(buffer, (byte) 2);
            }
            return null;
        }).when(collidingRandom).nextBytes(any(byte[].class));
        SessionService collidingService = new SessionService(
                userSessionRepository,
                webSocketSessionRegistry,
                tokenIntrospectionCache,
                collidingRandom,
                authProperties()
        );

        UUID userId = UUID.randomUUID();
        UserDB user = mock(UserDB.class);
        when(user.getId()).thenReturn(userId);

        UserSessionDto result = collidingService.yieldSession(user);

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        verify(collidingRandom, atLeast(4)).nextBytes(any(byte[].class));
    }

    @Test
    void invalidateSession_shouldNopWhenSessionDoesNotExist() {
        UUID sessionId = UUID.randomUUID();

        when(userSessionRepository.findById(sessionId))
                .thenReturn(Optional.empty());

        sessionService.invalidateSession(sessionId);

        verify(userSessionRepository).findById(sessionId);
        verify(userSessionRepository, never()).delete(any());
        verifyNoInteractions(tokenIntrospectionCache);
    }

    @Test
    void invalidateSession_shouldEvictAndDeleteSession() {
        UUID sessionId = UUID.randomUUID();
        UserSessionDB session = mock(UserSessionDB.class);

        when(userSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        sessionService.invalidateSession(sessionId);

        verify(tokenIntrospectionCache).evict(session);
        verify(userSessionRepository).delete(session);
    }

    @Test
    void invalidateSession_shouldEvictAgainAfterCommit() {
        UUID sessionId = UUID.randomUUID();
        UserSessionDB session = mock(UserSessionDB.class);

        when(userSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        sessionService.invalidateSession(sessionId);

        verify(tokenIntrospectionCache, times(1)).evict(session);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.getFirst().afterCommit();
        verify(tokenIntrospectionCache, times(2)).evict(session);
    }

    @Test
    void refreshAccessToken_yieldsValidAccessToken() {
        UUID sessionId = UUID.randomUUID();
        UserDB user = mock(UserDB.class);

        UserSessionDB userSession = mock(UserSessionDB.class);
        when(userSession.getRefreshTokenExpires()).thenReturn(Instant.now().plusSeconds(10000));
        when(userSession.getUser()).thenReturn(user);
        when(userSessionRepository.findById(sessionId)).thenReturn(Optional.of(userSession));
        when(userSessionRepository.save(any(UserSessionDB.class))).thenAnswer(invocation -> {
            UserSessionDB session = invocation.getArgument(0);
            return UserSessionDB.builder()
                    .id(UUID.randomUUID())
                    .user(session.getUser())
                    .accessToken(session.getAccessToken())
                    .accessTokenExpires(session.getAccessTokenExpires())
                    .refreshToken(session.getRefreshToken())
                    .refreshTokenExpires(session.getRefreshTokenExpires())
                    .build();
        });

        UserSessionDto result = sessionService.refreshAccessToken(sessionId);
        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.accessTokenExpires() > Instant.now().toEpochMilli()).isTrue();
        assertThat(result.refreshTokenExpires() > Instant.now().toEpochMilli()).isTrue();
        verify(tokenIntrospectionCache).evict(userSession);
        verify(userSessionRepository).delete(userSession);
    }

    @Test
    void refreshAccessToken_rejectsExpiredRefreshToken() {
        UUID sessionId = UUID.randomUUID();

        UserSessionDB userSession = mock(UserSessionDB.class);
        when(userSession.getRefreshTokenExpires()).thenReturn(Instant.now().minusSeconds(1));
        when(userSessionRepository.findById(sessionId)).thenReturn(Optional.of(userSession));

        assertThatThrownBy(() -> sessionService.refreshAccessToken(sessionId))
                .isInstanceOf(LoginFailedException.class);
    }

    @Test
    void refreshAccessToken_rejectsInvalidRefreshToken() {
        UUID sessionId = UUID.randomUUID();

        when(userSessionRepository.findById(sessionId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> sessionService.refreshAccessToken(sessionId))
                .isInstanceOf(LoginFailedException.class);
    }
}
