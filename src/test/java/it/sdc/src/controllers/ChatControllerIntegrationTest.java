package it.sdc.src.controllers;

import it.sdc.src.db.entities.ChatDB;
import it.sdc.src.db.entities.MessageDB;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.ChatDBRepository;
import it.sdc.src.db.repositories.MessageDBRepository;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import it.sdc.src.dto.ChatDto;
import it.sdc.src.dto.MessageDto;
import it.sdc.src.dto.requests.MessageRequest;
import it.sdc.src.service.mapping.MessageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

import static it.sdc.src.test.fixtures.BearerAuthFixtures.*;
import static it.sdc.src.test.fixtures.UserFixtures.mockUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
public class ChatControllerIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.1");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final Base64.Encoder ENCODER = Base64.getEncoder();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserDBRepository userRepository;

    @Autowired
    UserSessionDBRepository sessionRepository;

    @Autowired
    ChatDBRepository chatRepository;

    @Autowired
    MessageDBRepository messageRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    SecureRandom secureRandom;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MessageMapper messageMapper;

    private List<MessageDB> mockMessageHistory(ChatDB chat, int len) {
        List<MessageDB> messages = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            byte[] iv = new byte[16], data = new byte[128];
            secureRandom.nextBytes(iv);
            secureRandom.nextBytes(data);

            messages.add(messageRepository.save(
                    MessageDB.builder()
                            .iv(iv)
                            .data(data)
                            .sender(secureRandom.nextBoolean() ? chat.getUser1() : chat.getUser2())
                            .timestamp(Instant.now().truncatedTo(ChronoUnit.MILLIS))
                            .chat(chat)
                            .build()
            ));
        }

        return messages;
    }

    private List<MessageDB> mockMessageHistoryWithDistinctTimestamps(ChatDB chat, int len) {
        List<MessageDB> messages = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            byte[] iv = new byte[16], data = new byte[128];
            secureRandom.nextBytes(iv);
            secureRandom.nextBytes(data);

            messages.add(messageRepository.save(
                    MessageDB.builder()
                            .iv(iv)
                            .data(data)
                            .sender(secureRandom.nextBoolean() ? chat.getUser1() : chat.getUser2())
                            .timestamp(Instant.EPOCH.plus(i, ChronoUnit.SECONDS))
                            .chat(chat)
                            .build()
            ));
        }

        return messages;
    }

    private static ChatDB mockChat(UserDB user1, UserDB user2) {
        // Ensures user order (`.toString()` enforces SQL ordering)
        return ChatDB.builder()
                .user1(user1.getId().toString().compareTo(user2.getId().toString()) < 0 ? user1 : user2)
                .user2(user1.getId().toString().compareTo(user2.getId().toString()) < 0 ? user2 : user1)
                .build();
    }

    private static MessageRequest mockMessageRequest() {
        byte[] iv = new byte[12];
        Arrays.fill(iv, (byte) 1);

        return new MessageRequest(
                ENCODER.encodeToString(iv),
                ENCODER.encodeToString("Hello there!".getBytes())
        );
    }

    static Stream<MessageRequest> badMessageRequests() {
        String goodIV = ENCODER.encodeToString(new byte[] {1, 2}), goodData = ENCODER.encodeToString(new byte[] {3, 4, 5, 6, 7, 8});
        return Stream.of(
                null,
                new MessageRequest(goodData, null),
                new MessageRequest(goodData, ""),
                new MessageRequest(goodData, "definitely not Base64"),
                new MessageRequest(null, goodIV),
                new MessageRequest("", goodIV),
                new MessageRequest("definitely not Base64", goodIV)
        );
    }

    private void cleanupDb() {
        messageRepository.deleteAll();
        chatRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getMyChats_shouldListCurrentUserChats() throws Exception {
        cleanupDb();

        UserDB user = userRepository.save(mockUser(passwordEncoder, 1));
        UserSessionDB session = sessionRepository.save(mockSession(user));
        List<ChatDto> expectedChats = new ArrayList<>();

        for (int i = 2; i < 12; i++) {
            UserDB contact = userRepository.save(mockUser(passwordEncoder, i));
            ChatDB chat = chatRepository.save(mockChat(user, contact));
            MessageDB message = mockMessageHistory(chat, 1).getFirst();
            chat = chatRepository.findById(chat.getId()).orElseThrow();
            expectedChats.add(new ChatDto(
                    chat.getId(),
                    contact.getId(),
                    messageMapper.toDto(message, user.getId())
            ));
        }

        MvcResult result = mockMvc.perform(
                get("/chats")
                        .cookie(mockAccessCookie(session))
                        .accept(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk()).andReturn();

        List<ChatDto> userChats = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );

        // userChats is ordered by last message timestamp desc, expectedChats is obviously reversed
        assertThat(userChats).isEqualTo(expectedChats.reversed());
    }

    @Test
    void getMyChats_requiresAuth() throws Exception {
        mockMvc.perform(
                get("/chats").accept(MediaType.APPLICATION_JSON)
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void getMessageHistory_shouldReturnChatHistory() throws Exception {
        cleanupDb();

        UserDB user1 = userRepository.save(mockUser(passwordEncoder, 1));
        UserDB user2 = userRepository.save(mockUser(passwordEncoder, 2));
        UserSessionDB session = sessionRepository.save(mockSession(user1));
        ChatDB chat = chatRepository.save(mockChat(user1, user2));
        List<MessageDto> expected = mockMessageHistoryWithDistinctTimestamps(chat, 10).stream()
                .sorted(Comparator.comparing(MessageDB::getTimestamp).reversed())
                .map(msg -> messageMapper.toDto(msg, user1.getId()))
                .toList();

        MvcResult result = mockMvc.perform(
                get("/chats/{contactId}", user2.getId())
                        .param("n", "20")
                        .param("p", "0")
                        .cookie(mockAccessCookie(session))
                        .accept(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk()).andReturn();

        List<MessageDto> history = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );

        assertThat(history).isEqualTo(expected);
    }

    @Test
    void getMessageHistory_shouldPaginateResults() throws Exception {
        cleanupDb();

        UserDB user1 = userRepository.save(mockUser(passwordEncoder, 1));
        UserDB user2 = userRepository.save(mockUser(passwordEncoder, 2));
        UserSessionDB session = sessionRepository.save(mockSession(user1));
        ChatDB chat = chatRepository.save(mockChat(user1, user2));
        List<MessageDB> messages = mockMessageHistoryWithDistinctTimestamps(chat, 25);
        List<MessageDto> newestFirst = messages.stream()
                .sorted(Comparator.comparing(MessageDB::getTimestamp).reversed())
                .map(msg -> messageMapper.toDto(msg, user1.getId()))
                .toList();

        MvcResult firstPage = mockMvc.perform(
                get("/chats/{contactId}", user2.getId())
                        .param("n", "10")
                        .param("p", "0")
                        .cookie(mockAccessCookie(session))
                        .accept(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk()).andReturn();
        List<MessageDto> page0 = objectMapper.readValue(
                firstPage.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );

        MvcResult secondPage = mockMvc.perform(
                get("/chats/{contactId}", user2.getId())
                        .param("n", "10")
                        .param("p", "1")
                        .cookie(mockAccessCookie(session))
                        .accept(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk()).andReturn();
        List<MessageDto> page1 = objectMapper.readValue(
                secondPage.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );

        MvcResult thirdPage = mockMvc.perform(
                get("/chats/{contactId}", user2.getId())
                        .param("n", "10")
                        .param("p", "2")
                        .cookie(mockAccessCookie(session))
                        .accept(MediaType.APPLICATION_JSON)
        ).andExpect(status().isOk()).andReturn();
        List<MessageDto> page2 = objectMapper.readValue(
                thirdPage.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );

        assertThat(page0).hasSize(10).isEqualTo(newestFirst.subList(0, 10));
        assertThat(page1).hasSize(10).isEqualTo(newestFirst.subList(10, 20));
        assertThat(page2).hasSize(5).isEqualTo(newestFirst.subList(20, 25));
    }

    @Test
    void getMessageHistory_shouldRejectInvalidPaginationParams() throws Exception {
        cleanupDb();

        UserDB user1 = userRepository.save(mockUser(passwordEncoder, 1));
        UserDB user2 = userRepository.save(mockUser(passwordEncoder, 2));
        UserSessionDB session = sessionRepository.save(mockSession(user1));
        chatRepository.save(mockChat(user1, user2));

        mockMvc.perform(
                get("/chats/{contactId}", user2.getId())
                        .param("n", "0")
                        .param("p", "0")
                        .cookie(mockAccessCookie(session))
                        .accept(MediaType.APPLICATION_JSON)
        ).andExpect(status().isBadRequest());

        mockMvc.perform(
                get("/chats/{contactId}", user2.getId())
                        .param("n", "10")
                        .param("p", "-1")
                        .cookie(mockAccessCookie(session))
                        .accept(MediaType.APPLICATION_JSON)
        ).andExpect(status().isBadRequest());
    }

    @Test
    void getMessageHistory_requiresAuth() throws Exception {
        mockMvc.perform(
                get("/chats/{contactId}", UUID.randomUUID()).accept(MediaType.APPLICATION_JSON)
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void getMessageHistory_shouldErrorOnNonexistentChat() throws Exception {
        cleanupDb();

        UserDB user = userRepository.save(mockUser(passwordEncoder, 1));
        UserSessionDB session = sessionRepository.save(mockSession(user));

        mockMvc.perform(
                get("/chats/{contactId}", UUID.randomUUID())
                        .param("n", "10")
                        .param("p", "0")
                        .cookie(mockAccessCookie(session))
                        .accept(MediaType.APPLICATION_JSON)
        ).andExpect(status().isNotFound());
    }

    @Test
    void sendMessage_shouldSendMessage() throws Exception {
        cleanupDb();

        UserDB user = userRepository.save(mockUser(passwordEncoder, 1));
        UserDB contact = userRepository.save(mockUser(passwordEncoder, 2));
        UserSessionDB session = sessionRepository.save(mockSession(user));
        ChatDB chat = chatRepository.save(mockChat(user, contact));
        mockMessageHistory(chat, 1);

        MessageRequest request = mockMessageRequest();

        MvcResult result = mockMvc.perform(
                post("/chats/{contactId}", contact.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(mockAccessCookie(session))
                        .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isCreated()).andReturn();

        MessageDto sent = objectMapper.readValue(result.getResponse().getContentAsString(), MessageDto.class);
        assertThat(sent.iv()).isEqualTo(request.messageIV());
        assertThat(sent.data()).isEqualTo(request.messageData());
        assertThat(sent.direction()).isEqualTo(MessageDto.MessageDirection.OUTGOING);
    }

    @Test
    void sendMessage_shouldCreateChatIfNotExists() throws Exception {
        cleanupDb();

        UserDB user = userRepository.save(mockUser(passwordEncoder, 1));
        UserDB contact = userRepository.save(mockUser(passwordEncoder, 2));
        UserSessionDB session = sessionRepository.save(mockSession(user));

        MessageRequest request = mockMessageRequest();

        mockMvc.perform(
                post("/chats/{contactId}", contact.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(mockAccessCookie(session))
                        .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isCreated());

        List<ChatDB> chats = chatRepository.findByUserIdWithMessages(user.getId());
        assertThat(chats).hasSize(1);
        ChatDB chat = chats.getFirst();
        assertThat(chat.getMessages()).hasSize(1);
    }

    @Test
    void sendMessage_requiresAuth() throws Exception {
        mockMvc.perform(
                post("/chats/{contactId}", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockMessageRequest()))
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void sendMessage_shouldErrorOnNonexistentUser() throws Exception {
        cleanupDb();

        UserDB user = userRepository.save(mockUser(passwordEncoder, 1));
        UserSessionDB session = sessionRepository.save(mockSession(user));

        mockMvc.perform(
                post("/chats/{contactId}", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(mockAccessCookie(session))
                        .content(objectMapper.writeValueAsString(mockMessageRequest()))
        ).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @MethodSource("badMessageRequests")
    void sendMessage_shouldRejectInvalidMessageBodies(MessageRequest badRequest) throws Exception {
        cleanupDb();

        UserDB user = userRepository.save(mockUser(passwordEncoder, 1));
        UserSessionDB session = sessionRepository.save(mockSession(user));

        mockMvc.perform(
                post("/chats/{contactId}", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(mockAccessCookie(session))
                        .content(objectMapper.writeValueAsString(badRequest))
        ).andExpect(status().isBadRequest());
    }
}
