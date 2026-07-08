package it.sdc.src.controllers;

import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import it.sdc.src.dto.UserSessionDto;
import it.sdc.src.dto.requests.LoginRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

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
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.1");

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

    }

    @Test
    void register_shouldRejectBadRequests() throws Exception {

    }

    @Test
    void register_shouldRejectAlreadyTakenUsername() throws Exception {

    }

    @Test
    void register_shouldRejectWeakOrPwnedPasswords() throws Exception {

    }
}
