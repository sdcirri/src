package it.sdc.src.test.fixtures;

import it.sdc.src.auth.UserPrincipal;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.dto.UserSessionDto;
import it.sdc.src.service.AuthCookieService;
import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BearerAuthFixtures {
    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Map<UserSessionDB, SessionFixture> FIXTURES_BY_SESSION =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<UUID, SessionFixture> FIXTURES_BY_SESSION_ID = new ConcurrentHashMap<>();

    public record SessionFixture(
            UserSessionDB session,
            byte[] plainAccessToken,
            byte[] plainRefreshToken
    ) {}

    private static MessageDigest sha512() {
        try {
            return MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 hash algorithm is not available", e);
        }
    }

    private static byte[] randomToken() {
        byte[] token = new byte[32];
        SECURE_RANDOM.nextBytes(token);
        return token;
    }

    private static UserSessionDB clearId(UserSessionDB session) {
        try {
            Field idField = UserSessionDB.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(session, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to clear session id for persistence", e);
        }
        return session;
    }

    private static SessionFixture mockSessionFixture(UserDB user, Instant accessExpires, Instant refreshExpires) {
        byte[] plainAccess = randomToken();
        byte[] plainRefresh = randomToken();
        MessageDigest digest = sha512();
        UUID fixtureId = UUID.randomUUID();
        UserSessionDB session = UserSessionDB.builder()
                .id(fixtureId)
                .accessToken(digest.digest(plainAccess.clone()))
                .accessTokenExpires(accessExpires)
                .refreshToken(digest.digest(plainRefresh.clone()))
                .refreshTokenExpires(refreshExpires)
                .user(user)
                .build();
        return register(new SessionFixture(session, plainAccess, plainRefresh));
    }

    private static SessionFixture register(SessionFixture fixture) {
        FIXTURES_BY_SESSION.put(fixture.session(), fixture);
        if (fixture.session().getId() != null) {
            FIXTURES_BY_SESSION_ID.put(fixture.session().getId(), fixture);
        }
        return fixture;
    }

    private static SessionFixture requireFixture(UserSessionDB session) {
        SessionFixture fixture = FIXTURES_BY_SESSION.get(session);
        if (fixture == null && session.getId() != null) {
            fixture = FIXTURES_BY_SESSION_ID.get(session.getId());
        }
        if (fixture == null) {
            throw new IllegalStateException("Unknown session fixture");
        }
        if (session.getId() != null) {
            FIXTURES_BY_SESSION_ID.putIfAbsent(session.getId(), fixture);
        }
        return fixture;
    }

    public static SessionFixture mockSessionFixture(UserDB user) {
        return mockSessionFixture(user, Instant.now().plusSeconds(10000), Instant.now().plusSeconds(10000));
    }

    public static SessionFixture mockSessionFixtureWithExpiredAccessToken(UserDB user) {
        return mockSessionFixture(
                user,
                Instant.now().minusSeconds(10000),
                Instant.now().plusSeconds(10000)
        );
    }

    public static SessionFixture mockSessionFixtureWithExpiredRefreshToken(UserDB user) {
        return mockSessionFixture(
                user,
                Instant.now().plusSeconds(10000),
                Instant.now().minusSeconds(10000)
        );
    }

    public static UserSessionDB mockSession(UserDB user) {
        return clearId(mockSessionFixture(user).session());
    }

    public static UserSessionDB mockSessionWithExpiredAccessToken(UserDB user) {
        return clearId(mockSessionFixtureWithExpiredAccessToken(user).session());
    }

    public static UserSessionDB mockSessionWithExpiredRefreshToken(UserDB user) {
        return clearId(mockSessionFixtureWithExpiredRefreshToken(user).session());
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
        UUID sessionId = session.getId() != null ? session.getId() : UUID.randomUUID();
        return new UserPrincipal(
                session.getUser().getId(),
                sessionId,
                session.getUser().getUsername(),
                session.getAccessTokenExpires(),
                session.getRefreshTokenExpires()
        );
    }

    public static Cookie mockAccessCookie(UserSessionDB session) {
        return mockAccessCookie(requireFixture(session));
    }

    public static Cookie mockAccessCookie(SessionFixture fixture) {
        return new Cookie(
                AuthCookieService.ACCESS_COOKIE_NAME,
                ENCODER.encodeToString(fixture.plainAccessToken())
        );
    }

    public static Cookie mockRefreshCookie(UserSessionDB session) {
        return mockRefreshCookie(requireFixture(session));
    }

    public static Cookie mockRefreshCookie(SessionFixture fixture) {
        return new Cookie(
                AuthCookieService.REFRESH_COOKIE_NAME,
                ENCODER.encodeToString(fixture.plainRefreshToken())
        );
    }

    public static String encodedPlainAccessToken(UserSessionDB session) {
        return ENCODER.encodeToString(requireFixture(session).plainAccessToken());
    }

    public static String encodedPlainRefreshToken(UserSessionDB session) {
        return ENCODER.encodeToString(requireFixture(session).plainRefreshToken());
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
