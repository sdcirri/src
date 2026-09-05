package it.sdc.src.exceptions;

import it.sdc.src.auth.UserPrincipal;
import it.sdc.src.service.AuthCookieService;
import it.sdc.src.service.AuthService;
import it.sdc.src.service.ChatService;
import it.sdc.src.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import(GlobalExceptionHandler.class)
public class GlobalExceptionHandlerSliceTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    AuthCookieService authCookieService;

    @MockitoBean
    ChatService chatService;

    @MockitoBean
    UserService userService;

    @Test
    void should404onNoResourceFoundException() throws Exception {
        UserPrincipal principal = new UserPrincipal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user",
                Instant.now().plusSeconds(10000),
                Instant.now().plusSeconds(10000)
        );
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "test-token",
                Instant.now(),
                Instant.now().plusSeconds(10000)
        );
        BearerTokenAuthentication auth =
                new BearerTokenAuthentication(
                        principal,
                        accessToken,
                        principal.getAuthorities()
                );
        mockMvc.perform(
                get("/nothing")
                        .with(authentication(auth))
        ).andExpect(status().isNotFound());
    }
}
