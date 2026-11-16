package it.sdc.src.auth;

import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static it.sdc.src.utils.HashingUtils.sha512;

@Component
@RequiredArgsConstructor
public class TokenIntrospectionCache {
    private final UserSessionDBRepository sessionRepository;

    private final RedisTemplate<String, UserPrincipal> redis;

    private static final String ACCESS_PREFIX = "src:auth:access:";
    private static final String REFRESH_PREFIX = "src:auth:refresh:";

    private static final Duration ACCESS_TTL = Duration.ofMinutes(5);
    private static final Duration REFRESH_TTL = Duration.ofMinutes(15);

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
        String key = ACCESS_PREFIX + cacheKeyFromBearer(bearerToken);
        return introspect(
                key,
                ACCESS_TTL,
                () -> loadAccess(bearerToken),
                UserPrincipal::isExpired
        );
    }

    public UserPrincipal introspectRefreshToken(String bearerToken) {
        String key = REFRESH_PREFIX + cacheKeyFromBearer(bearerToken);
        return introspect(
                key,
                REFRESH_TTL,
                () -> loadRefresh(bearerToken),
                UserPrincipal::isRefreshExpired
        );
    }

    private UserPrincipal introspect(
            String key,
            Duration ttl,
            Supplier<UserPrincipal> loader,
            Predicate<UserPrincipal> isExpired
    ) {
        UserPrincipal principal = redis.opsForValue().get(key);
        boolean cacheMiss = principal == null;

        if (cacheMiss) principal = loader.get();

        if (isExpired.test(principal)) {
            redis.delete(key);
            throw new BadOpaqueTokenException("Bad auth");
        }

        if (cacheMiss)
            redis.opsForValue().set(key, principal, ttl);

        return principal;
    }

    public void evict(UserSessionDB session) {
        redis.delete(List.of(
                ACCESS_PREFIX + encodeHash(session.getAccessToken()),
                REFRESH_PREFIX + encodeHash(session.getRefreshToken())
        ));
    }

    public void evictAll(Collection<UserSessionDB> sessions) {
        List<String> keys = sessions.stream()
                .flatMap(session -> Stream.of(
                        ACCESS_PREFIX + encodeHash(session.getAccessToken()),
                        REFRESH_PREFIX + encodeHash(session.getRefreshToken())
                ))
                .toList();

        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
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
