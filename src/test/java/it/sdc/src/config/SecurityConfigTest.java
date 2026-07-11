package it.sdc.src.config;

import it.sdc.src.auth.AccessTokenIntrospector;
import it.sdc.src.auth.RefreshTokenIntrospector;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SecurityConfigTest {
    private final AppCorsProperties corsProperties = mock(AppCorsProperties.class);
    private final SecurityConfig config = new SecurityConfig(
            mock(AccessTokenIntrospector.class),
            mock(RefreshTokenIntrospector.class),
            corsProperties
    );

    @Test
    void passwordEncoder_hashesAndVerifiesCorrectly() {
        PasswordEncoder encoder = config.passwordEncoder();
        String hash = encoder.encode("hunter2");

        assertThat(hash).isNotEqualTo("hunter2");
        assertThat(encoder.matches("hunter2", hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }

    @Test
    void corsConfigurationSource_appliesConfiguredOrigins() {
        when(corsProperties.getAllowedOriginPatterns()).thenReturn(List.of("https://app.example.com"));

        CorsConfigurationSource source = config.corsConfigurationSource();
        var request = new MockHttpServletRequest();
        request.setRequestURI("/auth/login");

        CorsConfiguration resolved = source.getCorsConfiguration(request);
        assertThat(resolved).isNotNull();
        assertThat(resolved.getAllowedOriginPatterns()).containsExactly("https://app.example.com");
        assertThat(resolved.getExposedHeaders()).containsExactly("Authorization");
    }
}
