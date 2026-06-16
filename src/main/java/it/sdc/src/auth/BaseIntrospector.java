package it.sdc.src.auth;

import it.sdc.src.db.entities.UserSessionDB;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

import java.util.Base64;

public abstract class BaseIntrospector implements OpaqueTokenIntrospector {
    protected static byte[] decodeToken(String token) {
        try {
            return Base64.getDecoder().decode(token);
        }
        catch (IllegalArgumentException e) {
            throw new BadOpaqueTokenException("Bad opaque token", e);
        }
    }

    protected static UserPrincipal fromSession(UserSessionDB session) {
        return new UserPrincipal(
                session.getUser().getId(),
                session.getUser().getUsername(),
                session.getAccessTokenExpires(),
                session.getRefreshToken(),
                session.getRefreshTokenExpires()
        );
    }
}
