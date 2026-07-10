package it.sdc.src.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
public class ChatControllerTest {
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

    @Test
    void getMyChats_shouldListCurrentUserChats() throws Exception {

    }

    @Test
    void getMyChats_requiresAuth() throws Exception {

    }

    @Test
    void getMessageHistory_shouldReturnChatHistory() throws Exception {

    }

    @Test
    void getMessageHistory_requiresAuth() throws Exception {

    }

    @Test
    void getMessageHistory_shouldErrorOnNonexistentChat() throws Exception {

    }

    @Test
    void sendMessage_shouldSendMessage() throws Exception {

    }

    @Test
    void sendMessage_requiresAuth() throws Exception {

    }

    @Test
    void sendMessage_shouldErrorOnNonexistentUser() throws Exception {

    }

    @Test
    void getMessageHistory_shouldRejectInvalidMessageBodies() throws Exception {

    }
}
