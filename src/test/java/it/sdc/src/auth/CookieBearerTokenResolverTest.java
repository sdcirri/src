package it.sdc.src.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CookieBearerTokenResolverTest {
    private static final String COOKIE_NAME = "accessToken";

    private CookieBearerTokenResolver resolver;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        resolver = new CookieBearerTokenResolver(COOKIE_NAME);
        request = mock(HttpServletRequest.class);
    }

    @Test
    void resolve_noCookies_returnsNull() {
        when(request.getCookies()).thenReturn(null);
        assertThat(resolver.resolve(request)).isNull();
    }

    @Test
    void resolve_emptyCookieArray_returnsNull() {
        when(request.getCookies()).thenReturn(new Cookie[0]);
        assertThat(resolver.resolve(request)).isNull();
    }

    @Test
    void resolve_noMatchingCookie_returnsNull() {
        when(request.getCookies()).thenReturn(new Cookie[] {
                new Cookie("other", "value")
        });
        assertThat(resolver.resolve(request)).isNull();
    }

    @Test
    void resolve_matchingCookie_returnsValue() {
        when(request.getCookies()).thenReturn(new Cookie[] {
                new Cookie(COOKIE_NAME, "good-token")
        });
        assertThat(resolver.resolve(request)).isEqualTo("good-token");
    }

    @Test
    void resolve_multipleCookies_returnsMatchingValue() {
        when(request.getCookies()).thenReturn(new Cookie[] {
                new Cookie("other", "x"),
                new Cookie(COOKIE_NAME, "refresh-token")
        });
        assertThat(resolver.resolve(request)).isEqualTo("refresh-token");
    }

    @Test
    void resolve_matchingCookieWithNullValue_returnsNull() {
        Cookie cookie = new Cookie(COOKIE_NAME, null);
        when(request.getCookies()).thenReturn(new Cookie[] { cookie });
        assertThat(resolver.resolve(request)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void resolve_matchingCookieWithBlankValue_returnsNull(String value) {
        when(request.getCookies()).thenReturn(new Cookie[] {
                new Cookie(COOKIE_NAME, value)
        });
        assertThat(resolver.resolve(request)).isNull();
    }
}
