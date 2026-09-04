package it.sdc.src.test.fixtures;

import it.sdc.src.auth.UserPrincipal;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.dto.UserSessionDto;
import it.sdc.src.service.AuthCookieService;
import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BearerAuthFixtures {
    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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

    public static UserSessionDto mockSessionDto() {
        return new UserSessionDto(
                UUID.randomUUID(),
                java.util.Base64.getEncoder().encodeToString(new byte[32]),
                Instant.now().plusSeconds(3600).toEpochMilli(),
                java.util.Base64.getEncoder().encodeToString(new byte[32]),
                Instant.now().plusSeconds(1209600).toEpochMilli()
        );
    }

    public static UserPrincipal mockPrincipal(UserSessionDB session) {
        return new UserPrincipal(
                session.getUser().getId(),
                session.getUser().getUsername(),
                session.getAccessTokenExpires(),
                session.getRefreshToken(),
                session.getRefreshTokenExpires()
        );
    }

    public static Cookie mockAccessCookie(UserSessionDB session) {
        return new Cookie(
                AuthCookieService.ACCESS_COOKIE_NAME,
                ENCODER.encodeToString(session.getAccessToken())
        );
    }

    public static Cookie mockRefreshCookie(UserSessionDB session) {
        return new Cookie(
                AuthCookieService.REFRESH_COOKIE_NAME,
                ENCODER.encodeToString(session.getRefreshToken())
        );
    }

    public static List<String> setCookieHeaders(MvcResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
    }

    public static Optional<String> findCookieValue(MvcResult result, String cookieName) {
        String prefix = cookieName + "=";
        return setCookieHeaders(result).stream()
                .filter(header -> header.startsWith(prefix))
                .findFirst()
                .map(header -> {
                    String valuePart = header.substring(prefix.length());
                    int semicolon = valuePart.indexOf(';');
                    return semicolon < 0 ? valuePart : valuePart.substring(0, semicolon);
                });
    }
}
