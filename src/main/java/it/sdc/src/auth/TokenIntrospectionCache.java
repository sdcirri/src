package it.sdc.src.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;

@Component
@RequiredArgsConstructor
public class TokenIntrospectionCache {
    private final UserSessionDBRepository sessionRepository;
    private final MessageDigest sha512;

    private final Cache<String, UserPrincipal> accessCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    private final Cache<String, UserPrincipal> refreshCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(15))
            .build();

    private static byte[] decodeToken(String token) {
        try {
            return Base64.getDecoder().decode(token);
        } catch (IllegalArgumentException e) {
            throw new BadOpaqueTokenException("Bad opaque token", e);
        }
    }

    private static UserPrincipal fromSession(UserSessionDB session) {
        return new UserPrincipal(
                session.getUser().getId(),
                session.getId(),
                session.getUser().getUsername(),
                session.getAccessTokenExpires(),
                session.getRefreshTokenExpires()
        );
    }

    public UserPrincipal introspectAccessToken(String bearerToken) {
        UserPrincipal principal = accessCache.get(bearerToken, this::loadAccess);
        if (principal.isExpired()) {
            accessCache.invalidate(bearerToken);
            throw new BadOpaqueTokenException("Bad auth");
        }
        return principal;
    }

    public UserPrincipal introspectRefreshToken(String bearerToken) {
        UserPrincipal principal = refreshCache.get(bearerToken, this::loadRefresh);
        if (principal.isRefreshExpired()) {
            refreshCache.invalidate(bearerToken);
            throw new BadOpaqueTokenException("Bad auth");
        }
        return principal;
    }

    public void evict(UserSessionDB session) {
        accessCache.invalidate(key(session.getAccessToken()));
        refreshCache.invalidate(key(session.getRefreshToken()));
    }

    public void evictAll(Collection<UserSessionDB> sessions) {
        sessions.forEach(this::evict);
    }

    private UserPrincipal loadAccess(String bearerToken) {
        byte[] decoded = decodeToken(bearerToken);
        UserSessionDB session = sessionRepository.findByAccessToken(sha512.digest(decoded))
                .orElseThrow(() -> new BadOpaqueTokenException("Bad auth"));
        if (Instant.now().isAfter(session.getAccessTokenExpires()))
            throw new BadOpaqueTokenException("Bad auth");
        return fromSession(session);
    }

    private UserPrincipal loadRefresh(String bearerToken) {
        byte[] decoded = decodeToken(bearerToken);
        UserSessionDB session = sessionRepository.findByRefreshToken(sha512.digest(decoded))
                .orElseThrow(() -> new BadOpaqueTokenException("Bad auth"));
        if (Instant.now().isAfter(session.getRefreshTokenExpires()))
            throw new BadOpaqueTokenException("Bad auth");
        return fromSession(session);
    }

    private static String key(byte[] token) {
        return Base64.getEncoder().encodeToString(token);
    }
}
