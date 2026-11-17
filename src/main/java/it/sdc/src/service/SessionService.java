package it.sdc.src.service;

import it.sdc.src.auth.TokenIntrospectionCache;
import it.sdc.src.config.AuthProperties;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import it.sdc.src.dto.UserSessionDto;
import it.sdc.src.exceptions.LoginFailedException;
import it.sdc.src.ws.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static it.sdc.src.utils.HashingUtils.sha512;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final UserSessionDBRepository userSessionRepository;

    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final TokenIntrospectionCache tokenIntrospectionCache;
    private final SecureRandom secureRandom;

    private final AuthProperties authProperties;

    /**
     * Generate fresh access and refresh tokens
     * @return the new tokens
     */
    private byte[][] yieldTokens() {
        byte[] accessToken = new byte[32], refreshToken = new byte[32];
        do {
            secureRandom.nextBytes(accessToken);
            secureRandom.nextBytes(refreshToken);
            // worth checking even if possibility is astronomically low
        }  while (Arrays.equals(accessToken, refreshToken));
        return new byte[][]{accessToken, refreshToken};
    }

    /**
     * Generate a new session for the user
     * @param user user
     * @return the plain session tokens
     */
    @Transactional
    public UserSessionDto yieldSession(UserDB user) {
        byte[][] tokens = yieldTokens();
        Instant now = Instant.now();
        Instant accessExp = now.plusSeconds(authProperties.getAccessTokenValiditySeconds());
        Instant refreshExp = now.plusSeconds(authProperties.getRefreshTokenValiditySeconds());
        userSessionRepository.save(UserSessionDB.builder()
                .user(user)
                .accessToken(sha512(tokens[0]))
                .accessTokenExpires(accessExp)
                .refreshToken(sha512(tokens[1]))
                .refreshTokenExpires(refreshExp)
                .build()
        );
        return new UserSessionDto(
                user.getId(),
                Base64.getEncoder().encodeToString(tokens[0]),
                accessExp.toEpochMilli(),
                Base64.getEncoder().encodeToString(tokens[1]),
                refreshExp.toEpochMilli()
        );
    }

    /**
     * Invalidates a user session
     * @param sessionId session ID from the authentication principal
     */
    @Transactional
    public void invalidateSession(UUID sessionId) {
        UserSessionDB session = userSessionRepository.findById(sessionId).orElse(null);
        if (session == null) return;

        tokenIntrospectionCache.evict(session);
        userSessionRepository.delete(session);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        tokenIntrospectionCache.evict(session);
                        webSocketSessionRegistry.revoke(session.getId());
                    }
                }
        );
    }

    /**
     * Refresh a user session
     * @param sessionId session ID
     * @return a new user session
     * @throws LoginFailedException on bad refresh token
     */
    @Transactional
    public UserSessionDto refreshAccessToken(UUID sessionId) {
        UserSessionDB session = userSessionRepository.findById(sessionId)
                .orElseThrow(() -> new LoginFailedException("Invalid refresh token"));
        if(Instant.now().isAfter(session.getRefreshTokenExpires()))
            throw new LoginFailedException("Invalid refresh token");

        tokenIntrospectionCache.evict(session);
        userSessionRepository.delete(session);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        tokenIntrospectionCache.evict(session);
                        webSocketSessionRegistry.revoke(session.getId());
                    }
                }
        );
        return yieldSession(session.getUser());
    }

    /**
     * Invalidate all user sessions, then return a valid one.
     * Useful for password change, when old sessions must be disconnected
     * @param user user whose sessions should be invalidated
     * @return the new session
     */
    @Transactional
    public UserSessionDto replaceSessions(UserDB user) {
        List<UserSessionDB> oldSessions = userSessionRepository.findAllByUser_Id(user.getId());
        UserSessionDto newSession = yieldSession(user);

        tokenIntrospectionCache.evictAll(oldSessions);
        userSessionRepository.deleteAll(oldSessions);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        tokenIntrospectionCache.evictAll(oldSessions);
                        for (UserSessionDB session : oldSessions)
                            webSocketSessionRegistry.revoke(session.getId());
                    }
                }
        );
        return newSession;
    }
}
