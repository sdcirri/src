package it.sdc.src.auth;

import jakarta.annotation.Nonnull;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

import java.time.Instant;
import java.util.*;

@Getter
public class UserPrincipal implements OAuth2AuthenticatedPrincipal {
    private final UUID userId, sessionId;
    private final String username;
    private final Instant validUntil, refreshTokenValidUntil;
    private final Map<String, Object> attributes;

    public UserPrincipal(UUID userId, UUID sessionId, String username, Instant validUntil, Instant refreshTokenValidUntil) {
        this.userId = Objects.requireNonNull(userId);
        this.sessionId = Objects.requireNonNull(sessionId);
        this.username = Objects.requireNonNull(username);
        this.validUntil = Objects.requireNonNull(validUntil);
        this.refreshTokenValidUntil = Objects.requireNonNull(refreshTokenValidUntil);
        this.attributes = Map.of(
                "userId",     userId,
                "username",   username,
                "validUntil", validUntil
        );
    }

    @Override
    public @Nonnull Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public @Nonnull Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public @Nonnull String getName() {
        return username;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(validUntil);
    }

    public boolean isRefreshExpired() {
        return Instant.now().isAfter(refreshTokenValidUntil);
    }
}
