package it.sdc.src.ws;

import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import it.sdc.src.test.fixtures.BearerAuthFixtures;
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

import java.util.Base64;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static it.sdc.src.test.fixtures.BearerAuthFixtures.mockSessionFixture;
import static it.sdc.src.test.fixtures.UserFixtures.mockUser;
import static org.assertj.core.api.Assertions.*;

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
    private PasswordEncoder passwordEncoder;

    private StompSession connect(String accessToken) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();

        if (accessToken != null)
            headers.add(HttpHeaders.COOKIE, "accessToken=" + accessToken);

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
        stompClient.stop();
    }

    @Test
    void connect_withoutAuthentication_shouldBeRejected() {
        assertThatThrownBy(() -> connect(null))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("401");
    }
}
