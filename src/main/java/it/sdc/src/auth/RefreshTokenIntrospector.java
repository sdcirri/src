package it.sdc.src.auth;

import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class RefreshTokenIntrospector implements OpaqueTokenIntrospector {
    private final UserSessionDBRepository userSessionRepository;

    @Override
    public @Nonnull UserPrincipal introspect(@NonNull String token) {
        byte[] decoded;

        try {
            decoded = Base64.getDecoder().decode(token);
        }
        catch (IllegalArgumentException e) {
            throw new BadOpaqueTokenException("Bad opaque token", e);
        }

        UserSessionDB session = userSessionRepository.findByRefreshToken(decoded)
                .orElseThrow(() -> new BadOpaqueTokenException("Bad auth"));
        if (Instant.now().isAfter(session.getAccessTokenExpires()))
            throw new BadOpaqueTokenException("Bad auth");
        return new UserPrincipal(
                session.getId(),
                session.getUser().getUsername(),
                session.getAccessTokenExpires(),
                session.getRefreshToken(),
                session.getRefreshTokenExpires()
        );
    }
}
