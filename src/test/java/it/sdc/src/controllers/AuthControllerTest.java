package it.sdc.src.controllers;

import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import it.sdc.src.dto.UserSessionDto;
import it.sdc.src.dto.requests.LoginRequest;
import it.sdc.src.dto.requests.UserRegistrationRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
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

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Stream;

import static it.sdc.src.test.fixtures.BearerAuthFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
public class AuthControllerTest {
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
    PasswordEncoder passwordEncoder;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    SecureRandom secureRandom;

    private static Validator validator;

    private static Stream<LoginRequest> invalidLoginRequests() {
        return Stream.of(
                new LoginRequest("username", null),
                new LoginRequest("a weird username", null),
                new LoginRequest("username", ""),
                new LoginRequest(null, "P@$$w0rd!!!"),
                new LoginRequest("", "P@$$w0rd!!!"),
                new LoginRequest("xx", "P@$$w0rd!!!"),
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
                new UserRegistrationRequest(null, null, "P@$$w0rd!!!"),
                new UserRegistrationRequest("", null, "P@$$w0rd!!!"),
                new UserRegistrationRequest("a weird username", null, "P@$$w0rd!!!")
        );
    }

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void login_shouldAcceptValidCredentialsAndYieldSession() throws Exception {
        UserDB mockUser = mockUser(passwordEncoder);
        userRepository.deleteAll();
        userRepository.save(mockUser);

        LoginRequest req = new LoginRequest("username", "P@$$w0rd!!!");
        MvcResult result = mockMvc.perform(
                post("/auth/login")
                    .contentType(String.valueOf(MediaType.APPLICATION_JSON))
                    .content(objectMapper.writeValueAsString(req))
        ).andExpect(status().isOk()).andReturn();

        UserSessionDto resp = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                UserSessionDto.class
        );

        assertThat(resp).isNotNull();
        Set<ConstraintViolation<UserSessionDto>> violations = validator.validate(resp);
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidLoginRequests")
    void login_shouldRejectNullOrBlankFields(LoginRequest badRequest) throws Exception {
        mockMvc.perform(
                post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest))
        ).andExpect(status().isBadRequest());
    }

    @Test
    void login_shouldRejectInvalidCredentials() throws Exception {
        UserDB mockUser = mockUser(passwordEncoder);
        userRepository.deleteAll();
        userRepository.save(mockUser);

        LoginRequest req = new LoginRequest("username", "12345678");
        mockMvc.perform(
                post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void login_validationShouldBlockNullCredentials() throws Exception {
        LoginRequest nullPassword = new LoginRequest("username", null);
        LoginRequest nullUsername = new LoginRequest(null, "P@$$w0rd!!!");
        mockMvc.perform(
                post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullPassword))
        ).andExpect(status().isBadRequest());
        mockMvc.perform(
                post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullUsername))
        ).andExpect(status().isBadRequest());
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
                        .header(HttpHeaders.AUTHORIZATION, mockBearerRefreshTokenHeader(userSession))
                        .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk()).andReturn();

        UserSessionDto newSession = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                UserSessionDto.class
        );

        assertThat(newSession).isNotNull();
        Set<ConstraintViolation<UserSessionDto>> violations = validator.validate(newSession);
        assertThat(violations).isEmpty();

        assertThat(newSession.accessTokenExpires()).isGreaterThan(Instant.now().toEpochMilli());
        assertThat(newSession.refreshTokenExpires()).isGreaterThan(Instant.now().toEpochMilli());
        assertThat(newSession.refreshToken()).isNotEqualTo(
                Base64.getEncoder().encodeToString(userSession.getRefreshToken())
        );
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
                        .header(HttpHeaders.AUTHORIZATION, mockBearerRefreshTokenHeader(userSession))
                        .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void register_shouldPreRegisterUserOnGoodRequest() throws Exception {
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        UserRegistrationRequest request = new UserRegistrationRequest("username", null, "G1J0H!X4uTa6%ZJF");

        MvcResult result = mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isCreated()).andReturn();

        UserSessionDto session = objectMapper.readValue(result.getResponse().getContentAsString(), UserSessionDto.class);
        assertThat(session).isNotNull();
        Set<ConstraintViolation<UserSessionDto>> violations = validator.validate(session);
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidRegistrationRequests")
    void register_shouldRejectInvalidFields(UserRegistrationRequest badRequest) throws Exception {
        mockMvc.perform(
                post("/auth/register")
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
                "G1J0H!X4uTa6%ZJF"
        );

        mockMvc.perform(
                post("/auth/register")
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
                "username",
                null,
                password
        );

        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest))
        ).andExpect(status().isBadRequest());
    }

    @Test
    void finalizeRegistration_shouldFinalizeUserRegistration() throws Exception {

    }

    @Test
    void finalizeRegistration_shouldRejectUnauthenticatedCalls() throws Exception {

    }

    @Test
    void finalizeRegistration_shouldRejectFullyRegisteredUsers() throws Exception {

    }

    @Test
    void changePassword_shouldChangeUserPassword() throws Exception {

    }

    @Test
    void changePassword_shouldRejectOldPassword() throws Exception {

    }

    @Test
    void changePassword_shouldRejectNullOrBlankPassword() throws Exception {

    }

    @Test
    void changePassword_shouldRejectWeakOrPwnedPassword() throws Exception {

    }
}
