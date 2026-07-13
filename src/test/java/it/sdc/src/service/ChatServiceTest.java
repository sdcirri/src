package it.sdc.src.service;

import it.sdc.src.db.entities.ChatDB;
import it.sdc.src.db.entities.MessageDB;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.repositories.ChatDBRepository;
import it.sdc.src.db.repositories.MessageDBRepository;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.dto.MessageDto;
import it.sdc.src.dto.requests.MessageRequest;
import it.sdc.src.exceptions.ChatNotFoundException;
import it.sdc.src.exceptions.SelfChatException;
import it.sdc.src.exceptions.UserNotFoundException;
import it.sdc.src.service.mapping.ChatMapper;
import it.sdc.src.service.mapping.MessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ChatServiceTest {
    private ChatDBRepository chatRepository;
    private UserDBRepository userRepository;
    private MessageDBRepository messageRepository;
    private MessageMapper messageMapper;

    private ChatService chatService;

    private static final Base64.Encoder ENCODER = Base64.getEncoder();

    private static MessageRequest validMessageRequest() {
        return new MessageRequest(
                ENCODER.encodeToString("message".getBytes(StandardCharsets.UTF_8)),
                ENCODER.encodeToString("iv".getBytes(StandardCharsets.UTF_8))
        );
    }

    private List<MessageDB> mockMessageHistory(ChatDB chat, UserDB user1, UserDB user2) {
        List<MessageDB> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            MessageDB message = MessageDB.builder()
                    .id(UUID.randomUUID())
                    .chat(chat)
                    .timestamp(Instant.now())
                    .iv("iv".getBytes(StandardCharsets.UTF_8))
                    .data("data".getBytes(StandardCharsets.UTF_8))
                    .sender(i % 2 == 0 ? user1 : user2)
                    .build();
            messages.add(message);
        }
        return messages;
    }

    private static boolean specularHistories(List<MessageDto> history1, List<MessageDto> history2) {
        if (history1.size() != history2.size()) return false;
        for (int i = 0; i < history1.size(); i++) {
            // Must be identical except for direction
            MessageDto message1 = history1.get(i), message2 = history2.get(i);
            if (!message1.iv().equals(message2.iv())) return false;
            if (!message1.data().equals(message2.data())) return false;
            if (!message1.timestamp().equals(message2.timestamp())) return false;
            if (message1.direction().equals(message2.direction())) return false;
        }
        return true;
    }

    @BeforeEach
    void setUp() {
        chatRepository = mock(ChatDBRepository.class);
        userRepository = mock(UserDBRepository.class);
        messageRepository = mock(MessageDBRepository.class);
        ChatMapper chatMapper = mock(ChatMapper.class);
        messageMapper = mock(MessageMapper.class);

        chatService = new ChatService(
                chatRepository,
                userRepository,
                messageRepository,
                chatMapper,
                messageMapper
        );
    }

    @Test
    void sendMessage_shouldThrowSelfChatException_whenSenderAndContactAreSameUser() {
        UUID userId = UUID.randomUUID();

        MessageRequest request = new MessageRequest("data", "iv");

        assertThatThrownBy(() -> chatService.sendMessage(userId, userId, request))
                .isInstanceOf(SelfChatException.class)
                .hasMessage("You can't start new chat with yourself");

        verifyNoInteractions(userRepository, chatRepository, messageRepository);
    }

    @Test
    void sendMessage_shouldCreateNewChat_whenChatDoesNotExist() {
        UUID myUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID contactId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        UserDB user1 = mock(UserDB.class);
        UserDB user2 = mock(UserDB.class);
        ChatDB savedChat = mock(ChatDB.class);
        MessageDB savedMessage = mock(MessageDB.class);
        MessageDto expectedDto = mock(MessageDto.class);

        String plainData = "hello";
        String plainIv = "iv-value";

        MessageRequest request = new MessageRequest(
                Base64.getEncoder().encodeToString(plainData.getBytes(StandardCharsets.UTF_8)),
                Base64.getEncoder().encodeToString(plainIv.getBytes(StandardCharsets.UTF_8))
        );

        when(userRepository.findById(myUserId)).thenReturn(Optional.of(user1));
        when(userRepository.findById(contactId)).thenReturn(Optional.of(user2));
        when(chatRepository.findByUser1_IdAndUser2_Id(myUserId, contactId))
                .thenReturn(Optional.empty());
        when(chatRepository.save(any(ChatDB.class))).thenReturn(savedChat);
        when(messageRepository.save(any(MessageDB.class))).thenReturn(savedMessage);
        when(messageMapper.toDto(savedMessage, myUserId)).thenReturn(expectedDto);

        MessageDto result = chatService.sendMessage(myUserId, contactId, request);

        assertThat(result).isSameAs(expectedDto);

        verify(chatRepository).save(any(ChatDB.class));
        verify(messageRepository).save(any(MessageDB.class));
        verify(messageMapper).toDto(savedMessage, myUserId);
    }

    @Test
    void sendMessage_shouldReuseExistingChat_whenChatAlreadyExists() {
        UUID myUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID contactId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        UserDB user1 = mock(UserDB.class);
        UserDB user2 = mock(UserDB.class);
        ChatDB existingChat = mock(ChatDB.class);
        MessageDB savedMessage = mock(MessageDB.class);
        MessageDto expectedDto = mock(MessageDto.class);

        MessageRequest request = validMessageRequest();

        when(userRepository.findById(myUserId)).thenReturn(Optional.of(user1));
        when(userRepository.findById(contactId)).thenReturn(Optional.of(user2));
        when(chatRepository.findByUser1_IdAndUser2_Id(myUserId, contactId))
                .thenReturn(Optional.of(existingChat));
        when(messageRepository.save(any(MessageDB.class))).thenReturn(savedMessage);
        when(messageMapper.toDto(savedMessage, myUserId)).thenReturn(expectedDto);

        MessageDto result = chatService.sendMessage(myUserId, contactId, request);

        assertThat(result).isSameAs(expectedDto);

        verify(chatRepository, never()).save(any(ChatDB.class));
        verify(messageRepository).save(any(MessageDB.class));
    }

    @Test
    void sendMessage_shouldDecodeBase64PayloadBeforeSavingMessage() {
        UUID myUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID contactId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        UserDB user1 = mock(UserDB.class);
        UserDB user2 = mock(UserDB.class);
        ChatDB existingChat = mock(ChatDB.class);
        MessageDB savedMessage = mock(MessageDB.class);

        byte[] messageData = "encrypted-message".getBytes(StandardCharsets.UTF_8);
        byte[] messageIv = "initial-vector".getBytes(StandardCharsets.UTF_8);

        MessageRequest request = new MessageRequest(
                Base64.getEncoder().encodeToString(messageData),
                Base64.getEncoder().encodeToString(messageIv)
        );

        when(userRepository.findById(myUserId)).thenReturn(Optional.of(user1));
        when(userRepository.findById(contactId)).thenReturn(Optional.of(user2));
        when(chatRepository.findByUser1_IdAndUser2_Id(myUserId, contactId))
                .thenReturn(Optional.of(existingChat));
        when(messageRepository.save(any(MessageDB.class))).thenReturn(savedMessage);

        chatService.sendMessage(myUserId, contactId, request);

        ArgumentCaptor<MessageDB> captor = ArgumentCaptor.forClass(MessageDB.class);
        verify(messageRepository).save(captor.capture());

        MessageDB capturedMessage = captor.getValue();

        assertThat(capturedMessage.getData()).isEqualTo(messageData);
        assertThat(capturedMessage.getIv()).isEqualTo(messageIv);
    }

    @Test
    void sendMessage_shouldThrowUserNotFoundException_whenOwnUserDoesNotExist() {
        UUID myUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID contactId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        when(userRepository.findById(myUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(myUserId, contactId, validMessageRequest()))
                .isInstanceOf(UserNotFoundException.class);

        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_shouldThrowUserNotFoundException_whenContactUserDoesNotExist() {
        UUID myUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UserDB myUser = mock(UserDB.class);
        when(userRepository.findById(myUserId)).thenReturn(Optional.of(myUser));

        UUID contactId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        when(userRepository.findById(myUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(myUserId, contactId, validMessageRequest()))
                .isInstanceOf(UserNotFoundException.class);

        verify(messageRepository, never()).save(any());
    }

    @Test
    void getMessages_shouldReturnAllMessagesAndIsMirrored() {
        UUID myUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID contactId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UserDB myUser = mock(UserDB.class), contactUser = mock(UserDB.class);
        when(myUser.getId()).thenReturn(myUserId);
        when(contactUser.getId()).thenReturn(contactId);
        when(userRepository.findById(myUserId)).thenReturn(Optional.of(myUser));
        when(userRepository.findById(contactId)).thenReturn(Optional.of(contactUser));

        ChatDB chat = mock(ChatDB.class);
        when(chat.getMessages()).thenReturn(mockMessageHistory(chat, myUser, contactUser));
        when(chatRepository.findByUser1_IdAndUser2_Id(myUserId, contactId)).thenReturn(Optional.of(chat));
        when(messageMapper.toDto(any(MessageDB.class), any(UUID.class))).thenAnswer(invocation -> {
            MessageDB message = (MessageDB) invocation.getArguments()[0];
            UUID userId = (UUID) invocation.getArguments()[1];
            return new MessageDto(
                    message.getTimestamp().toEpochMilli(),
                    ENCODER.encodeToString(message.getData()),
                    ENCODER.encodeToString(message.getIv()),
                    message.getSender().getId().equals(userId) ? MessageDto.MessageDirection.OUTGOING : MessageDto.MessageDirection.INCOMING
            );
        });

        List<MessageDto> result1 = chatService.getMessages(myUserId, contactId);
        List<MessageDto> result2 = chatService.getMessages(contactId, myUserId);
        assertThat(specularHistories(result1, result2)).isTrue();
    }

    @Test
    void getMessages_shouldThrowChatNotFoundException_whenChatDoesNotExist() {
        UUID myUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID contactId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        when(chatRepository.findByUser1_IdAndUser2_Id(myUserId, contactId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getMessages(myUserId, contactId))
                .isInstanceOf(ChatNotFoundException.class);
    }
}
