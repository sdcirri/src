package it.sdc.src.controllers;

import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import it.sdc.src.dto.UserDto;
import it.sdc.src.service.mapping.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static it.sdc.src.test.fixtures.BearerAuthFixtures.mockBearerTokenHeader;
import static it.sdc.src.test.fixtures.BearerAuthFixtures.mockSession;
import static it.sdc.src.test.fixtures.UserFixtures.mockUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
public class UserControllerIntegrationTest {
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
    UserMapper userMapper;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void searchUsers_matchesUsernames() throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB myUser = userRepository.save(mockUser(passwordEncoder, 1));
        UserSessionDB mySession = sessionRepository.save(mockSession(myUser));

        for (int i = 2; i <= 6; i++)
            userRepository.save(mockUser(passwordEncoder, i));

        String goodPrefix = "user", badPrefix = "test";     // all mock users start with "user"

        MvcResult result = mockMvc.perform(
                get("/users/search?q=" + goodPrefix)
                        .header(HttpHeaders.AUTHORIZATION, mockBearerTokenHeader(mySession))
        ).andExpect(status().isOk()).andReturn();

        List<UserDto> results = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );

        assertThat(results).hasSize(5);             // current user should be excluded

        result = mockMvc.perform(
                get("/users/search?q=" + badPrefix)
                        .header(HttpHeaders.AUTHORIZATION, mockBearerTokenHeader(mySession))
        ).andExpect(status().isOk()).andReturn();

        results = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );

        assertThat(results).hasSize(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "User", "USER", "UsER"})
    void searchUsers_shouldBeCaseInsensitive(String query) throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB myUser = userRepository.save(mockUser(passwordEncoder, 1));
        userRepository.save(mockUser(passwordEncoder, 2));
        UserSessionDB mySession = sessionRepository.save(mockSession(myUser));

        MvcResult result = mockMvc.perform(
                get("/users/search?q=" + query)
                        .header(HttpHeaders.AUTHORIZATION, mockBearerTokenHeader(mySession))
        ).andExpect(status().isOk()).andReturn();

        List<UserDto> results = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );

        assertThat(results).hasSize(1);
    }

    @Test
    void searchUsers_pagesResults() throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB myUser = userRepository.save(mockUser(passwordEncoder, 1));
        UserSessionDB mySession = sessionRepository.save(mockSession(myUser));
        Set<UserDto> expected = new HashSet<>();

        for (int i = 2; i <= 21; i++) {
            UserDB user = userRepository.save(mockUser(passwordEncoder, i));
            expected.add(userMapper.toDto(user));
        }

        Set<UserDto> merged = new HashSet<>();
        List<UserDto> page;
        int n = 0;
        do {
            MvcResult result = mockMvc.perform(
                    get("/users/search?q=user&n=" + n++)    // all mock users start with "user"
                            .header(HttpHeaders.AUTHORIZATION, mockBearerTokenHeader(mySession))
            ).andExpect(status().isOk()).andReturn();

            page = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    new TypeReference<>() {}
            );

            assertThat(page).hasSizeLessThanOrEqualTo(10);

            merged.addAll(page);
        } while (!page.isEmpty());

        assertThat(merged).isEqualTo(expected);
    }

    @Test
    void searchUsers_shouldRejectNullQuery() throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB myUser = userRepository.save(mockUser(passwordEncoder, 1));
        UserSessionDB mySession = sessionRepository.save(mockSession(myUser));

        mockMvc.perform(
                get("/users/search")
                        .header(HttpHeaders.AUTHORIZATION, mockBearerTokenHeader(mySession))
        ).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 256, 1024, 2048})
    void searchUsers_querySizeShouldBeBounded(int querySize) throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB myUser = userRepository.save(mockUser(passwordEncoder, 1));
        UserSessionDB mySession = sessionRepository.save(mockSession(myUser));

        mockMvc.perform(
                get("/users/search?q=" + "A".repeat(querySize))
                        .header(HttpHeaders.AUTHORIZATION, mockBearerTokenHeader(mySession))
        ).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 255})
    void searchUsers_querySizeBoundaryIsInclusive(int querySize) throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB myUser = userRepository.save(mockUser(passwordEncoder, 1));
        UserSessionDB mySession = sessionRepository.save(mockSession(myUser));

        mockMvc.perform(
                get("/users/search?q=" + "A".repeat(querySize))
                        .header(HttpHeaders.AUTHORIZATION, mockBearerTokenHeader(mySession))
        ).andExpect(status().isOk());
    }

    @Test
    void searchUsers_requiresAuth() throws Exception {
        mockMvc.perform(
                get("/users/search?q=whatever")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void getUserInfo_shouldReturnValidInfo() throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB myUser = userRepository.save(mockUser(passwordEncoder, 1));
        UserDB otherUser = userRepository.save(mockUser(passwordEncoder, 2));
        UserSessionDB mySession = sessionRepository.save(mockSession(myUser));

        UserDto expected = userMapper.toDto(otherUser);

        MvcResult result = mockMvc.perform(
                get("/users/" + otherUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, mockBearerTokenHeader(mySession))
        ).andExpect(status().isOk()).andReturn();

        UserDto userInfo = objectMapper.readValue(result.getResponse().getContentAsString(), UserDto.class);
        assertThat(userInfo).isEqualTo(expected);
    }

    @Test
    void getUserInfo_shouldReturnOwnInfo() throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB myUser = userRepository.save(mockUser(passwordEncoder, 1));
        UserSessionDB mySession = sessionRepository.save(mockSession(myUser));

        UserDto expected = userMapper.toDto(myUser);

        MvcResult result = mockMvc.perform(
                get("/users/" + myUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, mockBearerTokenHeader(mySession))
        ).andExpect(status().isOk()).andReturn();

        UserDto userInfo = objectMapper.readValue(result.getResponse().getContentAsString(), UserDto.class);
        assertThat(userInfo).isEqualTo(expected);
    }

    @Test
    void getUserInfo_shouldErrorOnNonexistingUserId() throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB myUser = userRepository.save(mockUser(passwordEncoder, 1));
        UserSessionDB mySession = sessionRepository.save(mockSession(myUser));
        UUID badUserId;
        do {
            badUserId = UUID.randomUUID();
        } while (badUserId.equals(myUser.getId()));

        mockMvc.perform(
                get("/users/" + badUserId)
                        .header(HttpHeaders.AUTHORIZATION, mockBearerTokenHeader(mySession))
        ).andExpect(status().isNotFound());
    }

    @Test
    void getUserInfo_requiresAuth() throws Exception {
        mockMvc.perform(
                get("/users/" + UUID.randomUUID())
        ).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"aaaa", "123", "null"})
    void getUserInfo_shouldOnlyAcceptValidUUIDs(String badId) throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB myUser = userRepository.save(mockUser(passwordEncoder, 1));
        UserSessionDB mySession = sessionRepository.save(mockSession(myUser));

        mockMvc.perform(
                get("/users/" + badId)
                        .header(HttpHeaders.AUTHORIZATION, mockBearerTokenHeader(mySession))
        ).andExpect(status().isBadRequest());
    }
}
