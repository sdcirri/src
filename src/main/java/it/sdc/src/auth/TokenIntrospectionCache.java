package it.sdc.src.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;

@Component
@RequiredArgsConstructor
public class TokenIntrospectionCache {
    private final UserSessionDBRepository sessionRepository;

    private final Cache<String, UserPrincipal> accessCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    private final Cache<String, UserPrincipal> refreshCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(15))
            .build();

    private static byte[] sha512(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-512").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("SHA-512 hash algorithm is not available");
        }
    }

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
        String tokenKey = cacheKeyFromBearer(bearerToken);
        UserPrincipal principal = accessCache.get(tokenKey, _ -> loadAccess(bearerToken));
        if (principal.isExpired()) {
            accessCache.invalidate(tokenKey);
            throw new BadOpaqueTokenException("Bad auth");
        }
        return principal;
    }

    public UserPrincipal introspectRefreshToken(String bearerToken) {
        String tokenKey = cacheKeyFromBearer(bearerToken);
        UserPrincipal principal = refreshCache.get(tokenKey, _ -> loadRefresh(bearerToken));
        if (principal.isRefreshExpired()) {
            refreshCache.invalidate(tokenKey);
            throw new BadOpaqueTokenException("Bad auth");
        }
        return principal;
    }

    public void evict(UserSessionDB session) {
        accessCache.invalidate(encodeHash(session.getAccessToken()));
        refreshCache.invalidate(encodeHash(session.getRefreshToken()));
    }

    public void evictAll(Collection<UserSessionDB> sessions) {
        sessions.forEach(this::evict);
    }

    private UserPrincipal loadAccess(String bearerToken) {
        byte[] decoded = decodeToken(bearerToken);
        UserSessionDB session = sessionRepository.findByAccessToken(sha512(decoded))
                .orElseThrow(() -> new BadOpaqueTokenException("Bad auth"));
        if (Instant.now().isAfter(session.getAccessTokenExpires()))
            throw new BadOpaqueTokenException("Bad auth");
        return fromSession(session);
    }

    private UserPrincipal loadRefresh(String bearerToken) {
        byte[] decoded = decodeToken(bearerToken);
        UserSessionDB session = sessionRepository.findByRefreshToken(sha512(decoded))
                .orElseThrow(() -> new BadOpaqueTokenException("Bad auth"));
        if (Instant.now().isAfter(session.getRefreshTokenExpires()))
            throw new BadOpaqueTokenException("Bad auth");
        return fromSession(session);
    }

    private static String cacheKeyFromBearer(String bearerToken) {
        return encodeHash(sha512(decodeToken(bearerToken)));
    }

    private static String encodeHash(byte[] hash) {
        return Base64.getEncoder().encodeToString(hash);
    }
}
