package it.sdc.src.service;

import it.sdc.src.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthCookieService {
    public static final String ACCESS_COOKIE_NAME = "accessToken";
    public static final String REFRESH_COOKIE_NAME = "refreshToken";

    private final AuthProperties authProperties;

    /**
     * Build a secure cookie
     * @param cookieName cookie name
     * @param value cookie value
     * @param path cookie path
     * @param maxAge cookie max age
     * @return the cookie
     */
    private ResponseCookie buildCookie(String cookieName, String value, String path, long maxAge) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(authProperties.isCookieSecure())
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    /**
     * Clears a cookie
     * @param cookieName cookie name
     * @param path cookie path
     * @return the void cookie
     */
    private ResponseCookie clearCookie(String cookieName, String path) {
        return buildCookie(cookieName, "", path, 0);
    }

    /**
     * Builds the access cookie
     * @param accessToken access token
     * @return the access cookie
     */
    public ResponseCookie buildAccessCookie(String accessToken) {
        return buildCookie(
                ACCESS_COOKIE_NAME,
                accessToken,
                "/",
                authProperties.getAccessTokenValiditySeconds()
        );
    }

    /**
     * Clears the access cookie
     * @return the void access cookie
     */
    public ResponseCookie clearAccessCookie() {
        return clearCookie(ACCESS_COOKIE_NAME, "/");
    }

    /**
     * Builds the refresh cookie
     * @param refreshToken refresh token
     * @return the refresh cookie
     */
    public ResponseCookie buildRefreshCookie(String refreshToken) {
        return buildCookie(
                REFRESH_COOKIE_NAME,
                refreshToken,
                "/auth/refresh",
                authProperties.getRefreshTokenValiditySeconds()
        );
    }

    /**
     * Clears the refresh cookie
     * @return the void refresh cookie
     */
    public ResponseCookie clearRefreshCookie() {
        return clearCookie(REFRESH_COOKIE_NAME, "/auth/refresh");
    }
}
