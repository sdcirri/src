package it.sdc.src.controllers;

import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import it.sdc.src.dto.UserDto;
import it.sdc.src.dto.requests.accountedits.DisplayNameChangeRequest;
import it.sdc.src.dto.requests.accountedits.UsernameChangeRequest;
import it.sdc.src.service.ProPicNormalizer;
import it.sdc.src.service.mapping.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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

import java.awt.*;
import java.util.*;
import java.util.List;

import static it.sdc.src.test.fixtures.BearerAuthFixtures.mockBearerTokenHeader;
import static it.sdc.src.test.fixtures.BearerAuthFixtures.mockSession;
import static it.sdc.src.test.fixtures.GraphicsFixtures.createPngImage;
import static it.sdc.src.test.fixtures.UserFixtures.mockUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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

    @Autowired
    ProPicNormalizer proPicNormalizer;

    private static final Base64.Encoder ENCODER = Base64.getEncoder();

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

    @Test
    void setDisplayName_shouldChangeOwnDisplayName() throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB myUser = userRepository.save(mockUser(passwordEncoder, 1));
        UserSessionDB mySession = sessionRepository.save(mockSession(myUser));

        DisplayNameChangeRequest request = new DisplayNameChangeRequest("Cool display name");
        UserDto expected = new UserDto(
                myUser.getId(),
                myUser.getUsername(),
                request.displayName(),
                myUser.getProPic() == null ? null : ENCODER.encodeToString(myUser.getProPic())
        );

        MvcResult result = mockMvc.perform(
                put("/users/me/display_name")
                        .header(HttpHeaders.AUTHORIZATION, mockBearerTokenHeader(mySession))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isOk()).andReturn();

        UserDto newInfo = objectMapper.readValue(result.getResponse().getContentAsString(), UserDto.class);
        assertThat(newInfo).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 256, 1024})
    void setDisplayName_shouldRejectDisplayNameWithBadLength(int length) throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB myUser = userRepository.save(mockUser(passwordEncoder, 1));
        UserSessionDB mySession = sessionRepository.save(mockSession(myUser));

        DisplayNameChangeRequest request = new DisplayNameChangeRequest("A".repeat(length));

        mockMvc.perform(
                put("/users/me/display_name")
                        .header(HttpHeaders.AUTHORIZATION, mockBearerTokenHeader(mySession))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 255 })
    void setDisplayName_displayNameBoundaryIsInclusive(int length) throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB myUser = userRepository.save(mockUser(passwordEncoder, 1));
        UserSessionDB mySession = sessionRepository.save(mockSession(myUser));

        String displayName = "A".repeat(length);
        DisplayNameChangeRequest request = new DisplayNameChangeRequest(displayName);

        MvcResult result = mockMvc.perform(
                put("/users/me/display_name")
                        .header(HttpHeaders.AUTHORIZATION, mockBearerTokenHeader(mySession))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isOk()).andReturn();

        UserDto newInfo = objectMapper.readValue(result.getResponse().getContentAsString(), UserDto.class);
        assertThat(newInfo.displayName()).isEqualTo(request.displayName());
    }

    @Test
    void setDisplayName_requiresAuth() throws Exception {
        mockMvc.perform(
                get("/users/me/display_name")
        ).andExpect(status().isUnauthorized());
    }


    @Test
    void setUsername_shouldChangeOwnUsername() throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB myUser = userRepository.save(mockUser(passwordEncoder, 1));
        UserSessionDB mySession = sessionRepository.save(mockSession(myUser));

        UsernameChangeRequest request = new UsernameChangeRequest("cool_username_2");
        UserDto expected = new UserDto(
                myUser.getId(),
                request.username(),
                myUser.getDisplayName(),
                myUser.getProPic() == null ? null : ENCODER.encodeToString(myUser.getProPic())
        );

        MvcResult result = mockMvc.perform(
                put("/users/me/username")
                        .header(HttpHeaders.AUTHORIZATION, mockBearerTokenHeader(mySession))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isOk()).andReturn();

        UserDto newInfo = objectMapper.readValue(result.getResponse().getContentAsString(), UserDto.class);
        assertThat(newInfo).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 256, 1024})
    void setUsername_shouldRejectUsernameWithBadLength(int length) throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB myUser = userRepository.save(mockUser(passwordEncoder, 1));
        UserSessionDB mySession = sessionRepository.save(mockSession(myUser));

        UsernameChangeRequest request = new UsernameChangeRequest("A".repeat(length));

        mockMvc.perform(
                put("/users/me/username")
                        .header(HttpHeaders.AUTHORIZATION, mockBearerTokenHeader(mySession))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(ints = { 3, 255 })
    void setUsername_usernameBoundaryIsInclusive(int length) throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB myUser = userRepository.save(mockUser(passwordEncoder, 1));
        UserSessionDB mySession = sessionRepository.save(mockSession(myUser));

        String username = "A".repeat(length);
        UsernameChangeRequest request = new UsernameChangeRequest(username);

        MvcResult result = mockMvc.perform(
                put("/users/me/username")
                        .header(HttpHeaders.AUTHORIZATION, mockBearerTokenHeader(mySession))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isOk()).andReturn();

        UserDto newInfo = objectMapper.readValue(result.getResponse().getContentAsString(), UserDto.class);
        assertThat(newInfo.username()).isEqualTo(request.username());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a weird username",
            "no$pec1@lCh@rs@ll0w3d!!!1!"
    })
    void setUsername_shouldRejectBadUsernames(String username) throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB myUser = userRepository.save(mockUser(passwordEncoder, 1));
        UserSessionDB mySession = sessionRepository.save(mockSession(myUser));

        mockMvc.perform(
                put("/users/me/username")
                        .header(HttpHeaders.AUTHORIZATION, mockBearerTokenHeader(mySession))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UsernameChangeRequest(username)))
        ).andExpect(status().isBadRequest());
    }

    @Test
    void setUsername_requiresAuth() throws Exception {
        mockMvc.perform(
                get("/users/me/username")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void setProPic_shouldSetUserProPic() throws Exception {
        userRepository.deleteAll();
        sessionRepository.deleteAll();

        UserDB myUser = mockUser(passwordEncoder, 1);
        myUser.setProPic(null);
        myUser = userRepository.save(myUser);
        UserSessionDB mySession = sessionRepository.save(mockSession(myUser));

        byte[] newProPic = createPngImage(256, 256, Color.GREEN);
        UserDto expected = new UserDto(
                myUser.getId(),
                myUser.getUsername(),
                myUser.getDisplayName(),
                ENCODER.encodeToString(proPicNormalizer.normalizeImage(newProPic))
        );

        MockMultipartFile mockMultipartFile = new MockMultipartFile(
                "image", "photo.png", "image/png", newProPic
        );

        MvcResult result = mockMvc.perform(
                multipart(HttpMethod.PUT, "/users/me/propic")
                        .header(HttpHeaders.AUTHORIZATION, mockBearerTokenHeader(mySession))
                        .file(mockMultipartFile)
        ).andExpect(status().isOk()).andReturn();

        UserDto newInfo = objectMapper.readValue(result.getResponse().getContentAsString(), UserDto.class);
        assertThat(newInfo).isEqualTo(expected);
    }

    @Test
    void setProPic_shouldRejectEmptyMultipart() throws Exception {

    }

    @Test
    void setProPic_shouldRejectBadImage() throws Exception {

    }

    @Test
    void setProPic_requiresAuth() throws Exception {

    }
}
