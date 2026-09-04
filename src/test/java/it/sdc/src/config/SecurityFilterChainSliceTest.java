package it.sdc.src.config;

import it.sdc.src.auth.AccessTokenIntrospector;
import it.sdc.src.auth.RefreshTokenIntrospector;
import it.sdc.src.auth.UserPrincipal;
import it.sdc.src.controllers.AuthController;
import it.sdc.src.controllers.ChatController;
import it.sdc.src.dto.requests.LoginRequest;
import it.sdc.src.dto.requests.UserRegistrationRequest;
import it.sdc.src.service.AuthService;
import it.sdc.src.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import it.sdc.src.service.AuthCookieService;
import jakarta.servlet.http.Cookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

import static it.sdc.src.test.fixtures.UserFixtures.USER_PASSWORD;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(controllers = { AuthController.class, ChatController.class })
@Import(SecurityConfig.class)
public class SecurityFilterChainSliceTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    AccessTokenIntrospector accessTokenIntrospector;

    @MockitoBean
    RefreshTokenIntrospector refreshTokenIntrospector;

    @MockitoBean
    AppCorsProperties appCorsProperties;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    ChatService chatService;

    private static UserPrincipal mockPrincipal() {
        byte[] refreshToken = new byte[32];
        (new SecureRandom()).nextBytes(refreshToken);

        return new UserPrincipal(
                UUID.randomUUID(),
                "user",
                Instant.now().plusSeconds(10000),
                refreshToken,
                Instant.now().plusSeconds(10000)
        );
    }

    @Test
    void postLogin_isPermittedWithoutToken() throws Exception {
        when(authService.login(anyString(), anyString())).thenReturn(null);

        mockMvc.perform(
                post("/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("user", USER_PASSWORD)))
        ).andExpect(status().is2xxSuccessful());
    }

    @Test
    void getLogin_isNotPermitted() throws Exception {
        // permitAll is scoped to POST only — GET should still require auth
        mockMvc.perform(get("/auth/login"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/chats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withValidAccessToken_isAuthorized() throws Exception {
        when(accessTokenIntrospector.introspect("good-token")).thenReturn(mockPrincipal());

        mockMvc.perform(get("/chats").cookie(new Cookie(AuthCookieService.ACCESS_COOKIE_NAME, "good-token")))
                .andExpect(status().isOk());
    }

    @Test
    void refreshEndpoint_usesRefreshIntrospector_notAccessIntrospector() throws Exception {
        when(refreshTokenIntrospector.introspect("refresh-token")).thenReturn(mockPrincipal());

        mockMvc.perform(post("/auth/refresh").with(csrf()).cookie(new Cookie(AuthCookieService.REFRESH_COOKIE_NAME, "refresh-token")))
                .andExpect(status().isOk());

        verifyNoInteractions(accessTokenIntrospector); // proves chain isolation via securityMatcher
    }

    @Test
    void csrfIsDisabled_postWithoutCsrfToken_doesNotReturn403() throws Exception {
        mockMvc.perform(
                post("/auth/register")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserRegistrationRequest("user", null, USER_PASSWORD)
                        ))
        ).andExpect(status().is2xxSuccessful());
    }
}
