package it.sdc.src.test.fixtures;

import it.sdc.src.auth.UserPrincipal;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.entities.UserSessionDB;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public final class BearerAuthFixtures {
    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static UserDB mockUser(PasswordEncoder passwordEncoder) {
        return UserDB.builder()
                .username("username")
                .displayName("displayName")
                .passwordHash(passwordEncoder.encode("P@$$w0rd!!!"))
                .registrationTimeUTC(Instant.now())
                .build();
    }

    public static UserDB mockUserWithId(PasswordEncoder passwordEncoder) {
        return UserDB.builder()
                .id(UUID.randomUUID())
                .username("username")
                .displayName("displayName")
                .passwordHash(passwordEncoder.encode("P@$$w0rd!!!"))
                .registrationTimeUTC(Instant.now())
                .build();
    }

    public static UserSessionDB mockSession(UserDB user) {
        byte[] accessToken = new byte[32], refreshToken = new byte[32];
        SECURE_RANDOM.nextBytes(accessToken);
        SECURE_RANDOM.nextBytes(refreshToken);
        return UserSessionDB.builder()
                .accessToken(accessToken)
                .accessTokenExpires(Instant.now().plusSeconds(10000))
                .refreshToken(refreshToken)
                .refreshTokenExpires(Instant.now().plusSeconds(10000))
                .user(user)
                .build();
    }

    public static UserSessionDB mockSessionWithExpiredAccessToken(UserDB user) {
        byte[] accessToken = new byte[32], refreshToken = new byte[32];
        SECURE_RANDOM.nextBytes(accessToken);
        SECURE_RANDOM.nextBytes(refreshToken);
        return UserSessionDB.builder()
                .accessToken(accessToken)
                .accessTokenExpires(Instant.now().minusSeconds(10000))
                .refreshToken(refreshToken)
                .refreshTokenExpires(Instant.now().plusSeconds(10000))
                .user(user)
                .build();
    }

    public static UserSessionDB mockSessionWithExpiredRefreshToken(UserDB user) {
        byte[] accessToken = new byte[32], refreshToken = new byte[32];
        SECURE_RANDOM.nextBytes(accessToken);
        SECURE_RANDOM.nextBytes(refreshToken);
        return UserSessionDB.builder()
                .accessToken(accessToken)
                .accessTokenExpires(Instant.now().plusSeconds(10000))
                .refreshToken(refreshToken)
                .refreshTokenExpires(Instant.now().minusSeconds(10000))
                .user(user)
                .build();
    }

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
        return String.format("Bearer %s", ENCODER.encodeToString(session.getAccessToken()));
    }

    public static String mockBearerRefreshTokenHeader(UserSessionDB session) {
        return String.format("Bearer %s", ENCODER.encodeToString(session.getRefreshToken()));
    }
}
