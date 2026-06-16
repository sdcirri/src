package it.sdc.src.auth;

import jakarta.annotation.Nonnull;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

import java.time.Instant;
import java.util.*;

@Getter
public class UserPrincipal implements OAuth2AuthenticatedPrincipal {
    private final UUID userId;
    private final String username;
    private final Instant validUntil, refreshTokenValidUntil;
    private final byte[] refreshToken;
    private final Map<String, Object> attributes;

    public UserPrincipal(UUID userId, String username, Instant validUntil, byte[] refreshToken, Instant refreshTokenValidUntil) {
        this.userId = Objects.requireNonNull(userId);
        this.username = Objects.requireNonNull(username);
        this.validUntil = Objects.requireNonNull(validUntil);
        this.refreshTokenValidUntil = Objects.requireNonNull(refreshTokenValidUntil);
        this.refreshToken = Objects.requireNonNull(refreshToken);
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
}
