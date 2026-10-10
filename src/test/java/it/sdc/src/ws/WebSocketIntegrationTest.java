package it.sdc.src.ws;

import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.ChatDBRepository;
import it.sdc.src.db.repositories.MessageDBRepository;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import it.sdc.src.dto.MessageDto;
import it.sdc.src.dto.requests.MessageRequest;
import it.sdc.src.service.AuthCookieService;
import it.sdc.src.service.ChatService;
import it.sdc.src.test.fixtures.TestStompFrameHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static it.sdc.src.test.fixtures.BearerAuthFixtures.*;
import static it.sdc.src.test.fixtures.UserFixtures.mockUser;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class WebSocketIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.1");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    int port;

    WebSocketStompClient stompClient;

    @Autowired
    private UserDBRepository userRepository;

    @Autowired
    private UserSessionDBRepository userSessionRepository;

    @Autowired
    private ChatDBRepository chatRepository;

    @Autowired
    private MessageDBRepository messageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ChatService chatService;

    private static MessageRequest mockMessageRequest() {
        byte[] iv = new byte[12];
        Arrays.fill(iv, (byte) 1);

        return new MessageRequest(
                Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString("Hello there!".getBytes())
        );
    }

    private StompSession connect(String accessToken) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();

        if (accessToken != null)
            headers.add(HttpHeaders.COOKIE, AuthCookieService.ACCESS_COOKIE_NAME + "=" + accessToken);

        return stompClient
                .connectAsync(
                        "ws://localhost:" + port + "/ws",
                        headers,
                        new StompSessionHandlerAdapter() {}
                )
                .get(5, TimeUnit.SECONDS);
    }

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void tearDown() {
        messageRepository.deleteAll();
        chatRepository.deleteAll();
        userSessionRepository.deleteAll();
        userRepository.deleteAll();
        stompClient.stop();
    }

    @Test
    void connect_shouldAcceptUserCookie() throws Exception {
        UserDB user = userRepository.save(mockUser(passwordEncoder));
        UserSessionDB session = userSessionRepository.save(mockSession(user));
        String accessToken = encodedPlainAccessToken(session);

        StompSession stompSession = connect(accessToken);

        assertThat(stompSession).isNotNull();
        assertThat(stompSession.isConnected()).isTrue();
    }

    @Test
    void connect_withoutAuthentication_shouldBeRejected() {
        assertThatThrownBy(() -> connect(null))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("401");
    }

    @Test
    void connect_rejectsArbitrarySubscriptions() throws Exception {
        UserDB user = userRepository.save(mockUser(passwordEncoder));
        UserSessionDB session = userSessionRepository.save(mockSession(user));

        StompSession stompSession = connect(encodedPlainAccessToken(session));
        assertThat(stompSession.isConnected()).isTrue();

        stompSession.subscribe("/whatever", new TestStompFrameHandler(new LinkedBlockingQueue<>()));

        await().atMost(2, TimeUnit.SECONDS).until(() -> !stompSession.isConnected());
    }

    @Test
    void send_isNotAllowed() throws Exception {
        UserDB user = userRepository.save(mockUser(passwordEncoder));
        UserSessionDB session = userSessionRepository.save(mockSession(user));

        StompSession stompSession = connect(encodedPlainAccessToken(session));
        stompSession.send("/user/msgQueue/messages", new Object());

        await().atMost(2, TimeUnit.SECONDS).until(() -> !stompSession.isConnected());
    }

    @Test
    void sendMessage_generatesWSEvents() throws Exception {
        UserDB sender = userRepository.save(mockUser(passwordEncoder, 1));
        UserDB recipient = userRepository.save(mockUser(passwordEncoder, 2));
        UserSessionDB senderSession = userSessionRepository.save(mockSession(sender));
        UserSessionDB recipientSession = userSessionRepository.save(mockSession(recipient));
        String senderToken = encodedPlainAccessToken(senderSession);
        String recipientToken = encodedPlainAccessToken(recipientSession);

        StompSession senderStompSession = connect(senderToken), recipientStompSession = connect(recipientToken);
        BlockingQueue<MessageDto> senderMessages = new LinkedBlockingQueue<>(), recipientMessages = new LinkedBlockingQueue<>();
        senderStompSession.subscribe("/user/msgQueue/messages", new TestStompFrameHandler(senderMessages));
        recipientStompSession.subscribe("/user/msgQueue/messages", new TestStompFrameHandler(recipientMessages));

        MessageRequest request = mockMessageRequest();
        MessageDto restSent = chatService.sendMessage(sender.getId(), recipient.getId(), request);
        MessageDto wsSent = senderMessages.poll(3, TimeUnit.SECONDS);
        MessageDto received = recipientMessages.poll(3, TimeUnit.SECONDS);

        assertThat(restSent).isEqualTo(wsSent);

        assertThat(received).isNotNull();
        assertThat(received.id()).isEqualTo(restSent.id());
        assertThat(received.chatId()).isEqualTo(restSent.chatId());
        assertThat(received.senderId()).isEqualTo(sender.getId());
        assertThat(received.direction()).isEqualTo(MessageDto.MessageDirection.INCOMING);
        assertThat(received.data()).isEqualTo(request.messageData());
        assertThat(received.iv()).isEqualTo(request.messageIV());
    }

    @Test
    void sendMessage_eventsAreIsolated() throws Exception {
        UserDB sender = userRepository.save(mockUser(passwordEncoder, 1));
        UserDB recipient = userRepository.save(mockUser(passwordEncoder, 2));
        UserDB otherUser = userRepository.save(mockUser(passwordEncoder, 3));
        UserSessionDB otherUserSession = userSessionRepository.save(mockSession(otherUser));
        String otherUserToken = encodedPlainAccessToken(otherUserSession);

        StompSession otherUserStompSession = connect(otherUserToken);
        BlockingQueue<MessageDto> otherUserMessages = new LinkedBlockingQueue<>();
        otherUserStompSession.subscribe("/user/msgQueue/messages", new TestStompFrameHandler(otherUserMessages));

        chatService.sendMessage(sender.getId(), recipient.getId(), mockMessageRequest());
        MessageDto unexpected = otherUserMessages.poll(3, TimeUnit.SECONDS);

        assertThat(unexpected).isNull();
    }
}
