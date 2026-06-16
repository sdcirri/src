package it.sdc.src.auth;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenIntrospector implements OpaqueTokenIntrospector {
    private final TokenIntrospectionCache cache;

    @Override
    public @Nonnull UserPrincipal introspect(@Nonnull String token) {
        return cache.introspectRefreshToken(token);
    }
}
