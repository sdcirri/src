package it.sdc.src.auth;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessTokenIntrospector extends BaseIntrospector {
    private final TokenIntrospectionCache cache;

    @Override
    public @Nonnull UserPrincipal introspect(@Nonnull String token) {
        return cache.introspectAccessToken(token);
    }
}
