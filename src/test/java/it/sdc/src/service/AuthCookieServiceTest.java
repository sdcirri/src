package it.sdc.src.service;

import it.sdc.src.config.ApiProperties;
import it.sdc.src.config.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseCookie;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthCookieServiceTest {
    private AuthProperties authProperties;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        authProperties.setAccessTokenValiditySeconds(3600);
        authProperties.setRefreshTokenValiditySeconds(7200);
    }

    private AuthCookieService serviceFor(String apiBase) {
        ApiProperties apiProperties = new ApiProperties();
        apiProperties.setBase(apiBase);
        return new AuthCookieService(authProperties, apiProperties);
    }

    void assertSecureCookie(ResponseCookie responseCookie) {
        assertThat(responseCookie.getSameSite()).isEqualTo("Strict");
        assertThat(responseCookie.isHttpOnly()).isTrue();
        assertThat(responseCookie.isSecure()).isEqualTo(authProperties.isCookieSecure());
    }

    void assertAccessCookie(ResponseCookie responseCookie, String apiBase) {
        assertThat(responseCookie).isNotNull();
        assertThat(responseCookie.getName()).isEqualTo(AuthCookieService.ACCESS_COOKIE_NAME);
        assertThat(responseCookie.getPath()).isEqualTo(apiBase + "/");
        assertSecureCookie(responseCookie);
    }

    void assertRefreshCookie(ResponseCookie responseCookie, String apiBase) {
        assertThat(responseCookie).isNotNull();
        assertThat(responseCookie.getName()).isEqualTo(AuthCookieService.REFRESH_COOKIE_NAME);
        assertThat(responseCookie.getPath()).isEqualTo(apiBase + "/auth/refresh");
        assertSecureCookie(responseCookie);
    }

    void assertVoidCookie(ResponseCookie responseCookie) {
        assertThat(responseCookie).isNotNull();
        assertThat(responseCookie.getValue()).isEmpty();
        assertThat(responseCookie.getMaxAge().getSeconds()).isEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "/api" })
    void buildAccessCookie_buildsValidAccessCookie(String apiBase) {
        AuthCookieService authCookieService = serviceFor(apiBase);
        String accessToken = UUID.randomUUID().toString();
        ResponseCookie result = authCookieService.buildAccessCookie(accessToken);
        assertAccessCookie(result, apiBase);
        assertThat(result.getValue()).isEqualTo(accessToken);
        assertThat(result.getMaxAge().getSeconds()).isEqualTo(authProperties.getAccessTokenValiditySeconds());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "/api" })
    void clearAccessCookie_invalidatesAccessCookie(String apiBase) {
        AuthCookieService authCookieService = serviceFor(apiBase);
        ResponseCookie result = authCookieService.clearAccessCookie();
        assertAccessCookie(result, apiBase);
        assertVoidCookie(result);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "/api" })
    void buildRefreshCookie_buildsValidRefreshCookie(String apiBase) {
        AuthCookieService authCookieService = serviceFor(apiBase);
        String refreshToken = UUID.randomUUID().toString();
        ResponseCookie result = authCookieService.buildRefreshCookie(refreshToken);
        assertRefreshCookie(result, apiBase);
        assertThat(result.getValue()).isEqualTo(refreshToken);
        assertThat(result.getMaxAge().getSeconds()).isEqualTo(authProperties.getRefreshTokenValiditySeconds());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "/api" })
    void clearRefreshCookie_invalidatesRefreshCookie(String apiBase) {
        AuthCookieService authCookieService = serviceFor(apiBase);
        ResponseCookie result = authCookieService.clearRefreshCookie();
        assertRefreshCookie(result, apiBase);
        assertVoidCookie(result);
    }

    @Test
    void clearRefreshCookie_usesTheSamePathAsBuild() {
        AuthCookieService authCookieService = serviceFor("/api");
        assertThat(authCookieService.clearRefreshCookie().getPath())
                .isEqualTo(authCookieService.buildRefreshCookie("token").getPath());
    }
}
