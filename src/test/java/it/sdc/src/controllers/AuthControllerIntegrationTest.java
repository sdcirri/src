package it.sdc.src.controllers;

import it.sdc.src.db.entities.UserCryptoDB;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserCryptoDBRepository;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import it.sdc.src.dto.UserCryptoDto;
import it.sdc.src.dto.requests.LoginRequest;
import it.sdc.src.dto.requests.UserRegistrationFinalizationRequest;
import it.sdc.src.dto.requests.UserRegistrationRequest;
import it.sdc.src.dto.requests.accountedits.PasswordChangeRequest;
import it.sdc.src.service.AuthCookieService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static it.sdc.src.test.fixtures.BearerAuthFixtures.*;
import static it.sdc.src.test.fixtures.CryptoFixtures.mockFinalizationRequest;
import static it.sdc.src.test.fixtures.CryptoFixtures.mockUserCryptoDBSpecs;
import static it.sdc.src.test.fixtures.UserFixtures.USER_PASSWORD;
import static it.sdc.src.test.fixtures.UserFixtures.mockUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
public class AuthControllerIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.1");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserDBRepository userRepository;

    @Autowired
    UserSessionDBRepository sessionRepository;

    @Autowired
    UserCryptoDBRepository cryptoRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ObjectMapper objectMapper;

    private static final Base64.Encoder ENCODER = Base64.getEncoder();

    private static PasswordChangeRequest mockPasswordChangeRequest(String newPassword) {
        return new PasswordChangeRequest(
                newPassword,
                ENCODER.encodeToString(new byte[] {2}),
                ENCODER.encodeToString(new byte[] {3, 4, 5}),
                ENCODER.encodeToString(new byte[] {8, 9}),
                ENCODER.encodeToString(new byte[] {10, 11, 12}),
                ENCODER.encodeToString(new byte[] {13, 14})
        );
    }

    private static Stream<LoginRequest> invalidLoginRequests() {
        return Stream.of(
                new LoginRequest("username", null),
                new LoginRequest("a weird username", USER_PASSWORD),
                new LoginRequest("username", ""),
                new LoginRequest(null, USER_PASSWORD),
                new LoginRequest("", USER_PASSWORD),
                new LoginRequest("xx", USER_PASSWORD),
                new LoginRequest("username", "short"),
                new LoginRequest("", ""),
                new LoginRequest(null, null)
        );
    }

    private static Stream<UserRegistrationRequest> invalidRegistrationRequests() {
        return Stream.of(
                new UserRegistrationRequest(null, null, null),
                new UserRegistrationRequest("", null, ""),
                new UserRegistrationRequest("username", null, null),
                new UserRegistrationRequest("username", null, ""),
                new UserRegistrationRequest(null, null, USER_PASSWORD),
                new UserRegistrationRequest("", null, USER_PASSWORD),
                new UserRegistrationRequest("a weird username", null, USER_PASSWORD)
        );
    }

    private static Stream<PasswordChangeRequest> invalidPasswordChangeRequests() {
        return Stream.of(mockPasswordChangeRequest(""), mockPasswordChangeRequest(null));
    }

    private static void assertAuthSessionCookies(MvcResult result) {
        assertThat(findCookieValue(result, AuthCookieService.ACCESS_COOKIE_NAME)).isPresent();
        assertThat(findCookieValue(result, AuthCookieService.REFRESH_COOKIE_NAME)).isPresent();
        assertThat(findCookieValue(result, AuthCookieService.ACCESS_COOKIE_NAME).orElseThrow()).isNotBlank();
        assertThat(findCookieValue(result, AuthCookieService.REFRESH_COOKIE_NAME).orElseThrow()).isNotBlank();
    }

    @Test
    void login_shouldAcceptValidCredentialsAndYieldSession() throws Exception {
        UserDB mockUser = mockUser(passwordEncoder);
        userRepository.deleteAll();
        userRepository.save(mockUser);

        LoginRequest req = new LoginRequest("user1", USER_PASSWORD);
        MvcResult result = mockMvc.perform(
                post("/auth/login")
                        .with(csrf())
                    .contentType(String.valueOf(MediaType.APPLICATION_JSON))
                    .content(objectMapper.writeValueAsString(req))
        ).andExpect(status().isNoContent())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertAuthSessionCookies(result);
    }

    @ParameterizedTest
    @MethodSource("invalidLoginRequests")
    void login_shouldRejectNullOrBlankFields(LoginRequest badRequest) throws Exception {
        mockMvc.perform(
                post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest))
        ).andExpect(status().isBadRequest());
    }

    @Test
    void login_shouldRejectInvalidCredentials() throws Exception {
        UserDB mockUser = mockUser(passwordEncoder);
        userRepository.deleteAll();
        userRepository.save(mockUser);

        LoginRequest req = new LoginRequest("user1", "12345678");
        mockMvc.perform(
                post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void login_validationShouldBlockNullCredentials() throws Exception {
        LoginRequest nullPassword = new LoginRequest("user1", null);
        LoginRequest nullUsername = new LoginRequest(null, USER_PASSWORD);
        mockMvc.perform(
                post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullPassword))
        ).andExpect(status().isBadRequest());
        mockMvc.perform(
                post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullUsername))
        ).andExpect(status().isBadRequest());
    }

    @Test
    void logout_shouldClearCookies_whenAuthenticated() throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB user = userRepository.save(mockUser(passwordEncoder));
        UserSessionDB session = sessionRepository.save(mockSession(user));

        MvcResult result = mockMvc.perform(
                        post("/auth/logout")
                                .with(csrf())
                                .cookie(mockAccessCookie(session))
                                .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isNoContent())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
        List<String> headers = setCookieHeaders(result);
        assertThat(headers).hasSizeGreaterThanOrEqualTo(2);

        assertThat(headers.stream().anyMatch(h ->
                h.startsWith(AuthCookieService.ACCESS_COOKIE_NAME + "=") && h.contains("Max-Age=0"))
        ).isTrue();
        assertThat(headers.stream().anyMatch(h ->
                h.startsWith(AuthCookieService.REFRESH_COOKIE_NAME + "=") && h.contains("Max-Age=0"))
        ).isTrue();
    }

    @Test
    void logout_shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(
                post("/auth/logout")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_shouldYieldNewValidSession() throws Exception {
        UserDB user = mockUser(passwordEncoder);
        UserSessionDB userSession = mockSessionWithExpiredAccessToken(user);
        userRepository.deleteAll();
        userRepository.save(user);
        sessionRepository.deleteAll();
        sessionRepository.save(userSession);

        MvcResult result = mockMvc.perform(
                post("/auth/refresh")
                        .with(csrf())
                        .cookie(mockRefreshCookie(userSession))
                        .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isNoContent())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertAuthSessionCookies(result);

        String oldRefreshToken = ENCODER.encodeToString(userSession.getRefreshToken());
        String newRefreshToken = findCookieValue(result, AuthCookieService.REFRESH_COOKIE_NAME).orElseThrow();
        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);
    }

    @Test
    void refresh_shouldRejectInvalidRefreshToken() throws Exception {
        UserDB user = mockUser(passwordEncoder);
        UserSessionDB userSession = mockSessionWithExpiredRefreshToken(user);
        userRepository.deleteAll();
        userRepository.save(user);
        sessionRepository.deleteAll();
        sessionRepository.save(userSession);

        mockMvc.perform(
                post("/auth/refresh")
                        .with(csrf())
                        .cookie(mockRefreshCookie(userSession))
                        .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void register_shouldPreRegisterUserOnGoodRequest() throws Exception {
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        UserRegistrationRequest request = new UserRegistrationRequest("user1", null, USER_PASSWORD);

        MvcResult result = mockMvc.perform(
                post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isCreated())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertAuthSessionCookies(result);
    }

    @ParameterizedTest
    @MethodSource("invalidRegistrationRequests")
    void register_shouldRejectInvalidFields(UserRegistrationRequest badRequest) throws Exception {
        mockMvc.perform(
                post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest))
        ).andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldRejectAlreadyTakenUsername() throws Exception {
        UserDB existingUser = mockUser(passwordEncoder);
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(existingUser);

        UserRegistrationRequest request = new UserRegistrationRequest(
                existingUser.getUsername(),
                null,
                USER_PASSWORD
        );

        mockMvc.perform(
                post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "longbuttoosimple1!",       // no upper
            "LONGBUTTOOSIMPLE1!",       // no lower
            "LongButTooSimple1",        // no special
            "LongButTooSimple!",        // no number
            "Password1!"                // strong but pwned
    })
    void register_shouldRejectWeakOrPwnedPasswords(String password) throws Exception {
        UserRegistrationRequest badRequest = new UserRegistrationRequest(
                "user1",
                null,
                password
        );

        mockMvc.perform(
                post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest))
        ).andExpect(status().isBadRequest());
    }

    @Test
    void finalizeRegistration_shouldFinalizeUserRegistration() throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();
        cryptoRepository.deleteAll();

        UserDB user = userRepository.save(mockUser(passwordEncoder));
        UserSessionDB session = mockSession(user);
        sessionRepository.save(session);

        UserRegistrationFinalizationRequest request = mockFinalizationRequest();

        MvcResult result = mockMvc.perform(
                post("/auth/register/finalize")
                        .with(csrf())
                        .cookie(mockAccessCookie(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isOk()).andReturn();

        Optional<UserCryptoDB> userCrypto = cryptoRepository.findById(user.getId());
        assertThat(userCrypto).isPresent();
        UserCryptoDB userCryptoDB = userCrypto.get();

        assertThat(ENCODER.encodeToString(userCryptoDB.getKekSalt())).isEqualTo(request.kekSalt());
        assertThat(ENCODER.encodeToString(userCryptoDB.getPrivateEd25519())).isEqualTo(request.privateEd25519Crypto());
        assertThat(ENCODER.encodeToString(userCryptoDB.getIvEd25519())).isEqualTo(request.privateEd25519IV());
        assertThat(ENCODER.encodeToString(userCryptoDB.getPublicEd25519())).isEqualTo(request.publicEd25519());
        assertThat(ENCODER.encodeToString(userCryptoDB.getPrivateX25519())).isEqualTo(request.privateX25519Crypto());
        assertThat(ENCODER.encodeToString(userCryptoDB.getIvX25519())).isEqualTo(request.privateX25519IV());
        assertThat(ENCODER.encodeToString(userCryptoDB.getPublicX25519())).isEqualTo(request.publicX25519());

        UserCryptoDto cryptoDto = objectMapper.readValue(result.getResponse().getContentAsString(), UserCryptoDto.class);
        assertThat(cryptoDto).isNotNull();
        assertThat(cryptoDto.id()).isEqualTo(user.getId());
        assertThat(cryptoDto.kekSalt()).isEqualTo(request.kekSalt());
        assertThat(cryptoDto.privateEd25519Crypto()).isEqualTo(request.privateEd25519Crypto());
        assertThat(cryptoDto.privateEd25519IV()).isEqualTo(request.privateEd25519IV());
        assertThat(cryptoDto.publicEd25519()).isEqualTo(request.publicEd25519());
        assertThat(cryptoDto.privateX25519Crypto()).isEqualTo(request.privateX25519Crypto());
        assertThat(cryptoDto.privateX25519IV()).isEqualTo(request.privateX25519IV());
        assertThat(cryptoDto.publicX25519()).isEqualTo(request.publicX25519());
    }

    @Test
    void finalizeRegistration_shouldRejectUnauthenticatedCalls() throws Exception {
        mockMvc.perform(
                post("/auth/register/finalize")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockFinalizationRequest()))
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void finalizeRegistration_shouldRejectFullyRegisteredUsers() throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();
        cryptoRepository.deleteAll();

        UserDB user = userRepository.save(mockUser(passwordEncoder));
        UserSessionDB session = sessionRepository.save(mockSession(user));
        cryptoRepository.save(mockUserCryptoDBSpecs(user));

        mockMvc.perform(
                post("/auth/register/finalize")
                        .with(csrf())
                        .cookie(mockAccessCookie(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockFinalizationRequest()))
        ).andExpect(status().isConflict());
    }

    @Test
    void changePassword_shouldChangeUserPasswordAndEvictOldSessions() throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();
        cryptoRepository.deleteAll();

        UserDB user = userRepository.save(mockUser(passwordEncoder));

        for (int i = 0; i < 10; i++)
            sessionRepository.save(mockSession(user));
        UserSessionDB session = sessionRepository.save(mockSession(user));
        String oldRefreshToken = ENCODER.encodeToString(session.getRefreshToken());

        cryptoRepository.save(mockUserCryptoDBSpecs(user));

        assertThat(sessionRepository.findAllByUser_Id(user.getId()).size()).isEqualTo(11);
        assertThat(passwordEncoder.matches(USER_PASSWORD, user.getPasswordHash())).isTrue();

        String newPassword = "P@$$w0rd.123!!!";
        PasswordChangeRequest request = mockPasswordChangeRequest(newPassword);
        MvcResult result = mockMvc.perform(
                post("/auth/me/password")
                        .with(csrf())
                        .cookie(mockAccessCookie(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isNoContent())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertAuthSessionCookies(result);
        String newRefreshToken = findCookieValue(result, AuthCookieService.REFRESH_COOKIE_NAME).orElseThrow();
        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);
        assertThat(sessionRepository.findAllByUser_Id(user.getId()).size()).isEqualTo(1);

        // Also check whether crypto was updated successfully
        Optional<UserCryptoDB> userCryptoDBOptional = cryptoRepository.findById(user.getId());
        assertThat(userCryptoDBOptional).isPresent();
        UserCryptoDB userCryptoDB = userCryptoDBOptional.get();

        assertThat(ENCODER.encodeToString(userCryptoDB.getKekSalt())).isEqualTo(request.newKekSalt());
        assertThat(ENCODER.encodeToString(userCryptoDB.getPrivateEd25519())).isEqualTo(request.newPrivateEd25519());
        assertThat(ENCODER.encodeToString(userCryptoDB.getIvEd25519())).isEqualTo(request.newIvEd25519());
        assertThat(ENCODER.encodeToString(userCryptoDB.getPrivateX25519())).isEqualTo(request.newPrivateX25519());
        assertThat(ENCODER.encodeToString(userCryptoDB.getIvX25519())).isEqualTo(request.newIvX25519());
    }

    @Test
    void changePassword_shouldRejectOldPassword() throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB user = userRepository.save(mockUser(passwordEncoder));
        UserSessionDB session = sessionRepository.save(mockSession(user));

        assertThat(passwordEncoder.matches(USER_PASSWORD, user.getPasswordHash())).isTrue();
        mockMvc.perform(
                post("/auth/me/password")
                        .with(csrf())
                        .cookie(mockAccessCookie(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockPasswordChangeRequest(USER_PASSWORD)))
        ).andExpect(status().isConflict());
    }

    @ParameterizedTest
    @MethodSource("invalidPasswordChangeRequests")
    void changePassword_shouldRejectNullOrBlankPassword(PasswordChangeRequest badRequest) throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB user = userRepository.save(mockUser(passwordEncoder));
        UserSessionDB session = sessionRepository.save(mockSession(user));

        mockMvc.perform(
                post("/auth/me/password")
                        .with(csrf())
                        .cookie(mockAccessCookie(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest))
        ).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "longbuttoosimple1!",       // no upper
            "LONGBUTTOOSIMPLE1!",       // no lower
            "LongButTooSimple1",        // no special
            "LongButTooSimple!",        // no number
            "Password1!"                // strong but pwned
    })
    void changePassword_shouldRejectWeakOrPwnedPassword(String badPassword) throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB user = userRepository.save(mockUser(passwordEncoder));
        UserSessionDB session = sessionRepository.save(mockSession(user));

        mockMvc.perform(
                post("/auth/me/password")
                        .with(csrf())
                        .cookie(mockAccessCookie(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockPasswordChangeRequest(badPassword)))
        ).andExpect(status().isBadRequest());
    }
}
