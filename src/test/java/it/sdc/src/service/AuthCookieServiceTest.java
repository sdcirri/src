package it.sdc.src.service;

import it.sdc.src.config.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthCookieServiceTest {
    private AuthCookieService authCookieService;
    private AuthProperties authProperties;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        authProperties.setAccessTokenValiditySeconds(3600);
        authProperties.setRefreshTokenValiditySeconds(7200);
        authCookieService = new AuthCookieService(authProperties);
    }

    void assertSecureCookie(ResponseCookie responseCookie) {
        assertThat(responseCookie.getSameSite()).isEqualTo("Strict");
        assertThat(responseCookie.isHttpOnly()).isTrue();
        assertThat(responseCookie.isSecure()).isEqualTo(authProperties.isCookieSecure());
    }

    void assertAccessCookie(ResponseCookie responseCookie) {
        assertThat(responseCookie).isNotNull();
        assertThat(responseCookie.getName()).isEqualTo(AuthCookieService.ACCESS_COOKIE_NAME);
        assertThat(responseCookie.getPath()).isEqualTo("/");
        assertSecureCookie(responseCookie);
    }

    void assertRefreshCookie(ResponseCookie responseCookie) {
        assertThat(responseCookie).isNotNull();
        assertThat(responseCookie.getName()).isEqualTo(AuthCookieService.REFRESH_COOKIE_NAME);
        assertThat(responseCookie.getPath()).isEqualTo("/auth/refresh");
        assertSecureCookie(responseCookie);
    }

    void assertVoidCookie(ResponseCookie responseCookie) {
        assertThat(responseCookie).isNotNull();
        assertThat(responseCookie.getValue()).isEmpty();
        assertThat(responseCookie.getMaxAge().getSeconds()).isEqualTo(0);
    }

    @Test
    void buildAccessCookie_buildsValidAccessCookie() {
        String accessToken = UUID.randomUUID().toString();
        ResponseCookie result = authCookieService.buildAccessCookie(accessToken);
        assertAccessCookie(result);
        assertThat(result.getValue()).isEqualTo(accessToken);
        assertThat(result.getMaxAge().getSeconds()).isEqualTo(authProperties.getAccessTokenValiditySeconds());
    }

    @Test
    void clearAccessCookie_invalidatesAccessCookie() {
        ResponseCookie result = authCookieService.clearAccessCookie();
        assertAccessCookie(result);
        assertVoidCookie(result);
    }

    @Test
    void buildRefreshCookie_buildsValidRefreshCookie() {
        String refreshToken = UUID.randomUUID().toString();
        ResponseCookie result = authCookieService.buildRefreshCookie(refreshToken);
        assertRefreshCookie(result);
        assertThat(result.getValue()).isEqualTo(refreshToken);
        assertThat(result.getMaxAge().getSeconds()).isEqualTo(authProperties.getRefreshTokenValiditySeconds());
    }

    @Test
    void clearRefreshCookie_invalidatesRefreshCookie() {
        ResponseCookie result = authCookieService.clearRefreshCookie();
        assertRefreshCookie(result);
        assertVoidCookie(result);
    }
}
