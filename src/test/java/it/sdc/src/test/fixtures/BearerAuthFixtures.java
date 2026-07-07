package it.sdc.src.test.fixtures;

import it.sdc.src.auth.UserPrincipal;
import it.sdc.src.db.entities.UserSessionDB;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;

import java.util.Base64;

public final class BearerAuthFixtures {
    private static final Base64.Encoder ENCODER = Base64.getEncoder();

    private static UserPrincipal mockPrincipal(UserSessionDB session) {
        return new UserPrincipal(
                session.getUser().getId(),
                session.getUser().getUsername(),
                session.getAccessTokenExpires(),
                session.getRefreshToken(),
                session.getRefreshTokenExpires()
        );
    }

    public static BearerTokenAuthentication mockBearerTokenAuthentication(UserSessionDB session) {
        return new BearerTokenAuthentication(
                mockPrincipal(session),
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        ENCODER.encodeToString(session.getAccessToken()),
                        session.getAccessTokenExpires().minusSeconds(1),
                        session.getAccessTokenExpires()
                ),
                null
        );
    }

    public static BearerTokenAuthentication mockBearerRefreshTokenAuthentication(UserSessionDB session) {
        return new BearerTokenAuthentication(
                mockPrincipal(session),
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        ENCODER.encodeToString(session.getRefreshToken()),
                        session.getRefreshTokenExpires().minusSeconds(1),
                        session.getRefreshTokenExpires()
                ),
                null
        );
    }

    public static String mockBearerTokenHeader(UserSessionDB session) {
        return String.format("earer %s", ENCODER.encodeToString(session.getAccessToken()));
    }

    public static String mockBearerRefreshTokenHeader(UserSessionDB session) {
        return String.format("Bearer %s", ENCODER.encodeToString(session.getRefreshToken()));
    }
}
