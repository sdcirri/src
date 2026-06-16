package it.sdc.src.auth;

import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class AccessTokenIntrospector extends BaseIntrospector {
    private final UserSessionDBRepository userSessionRepository;

    @Override
    public @Nonnull UserPrincipal introspect(@NonNull String token) {
        byte[] decoded = decodeToken(token);

        UserSessionDB session = userSessionRepository.findByAccessToken(decoded)
                .orElseThrow(() -> new BadOpaqueTokenException("Bad auth"));
        if (Instant.now().isAfter(session.getAccessTokenExpires()))
            throw new BadOpaqueTokenException("Bad auth");
        return fromSession(session);
    }
}
