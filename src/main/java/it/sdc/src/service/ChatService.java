package it.sdc.src.service;

import it.sdc.src.db.entities.ChatDB;
import it.sdc.src.db.entities.MessageDB;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.repositories.ChatDBRepository;
import it.sdc.src.db.repositories.MessageDBRepository;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.dto.ChatDto;
import it.sdc.src.dto.MessageDto;
import it.sdc.src.dto.requests.accountedits.MessageRequest;
import it.sdc.src.exceptions.ChatNotFoundException;
import it.sdc.src.exceptions.SelfChatException;
import it.sdc.src.exceptions.UserNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatDBRepository chatRepository;
    private final UserDBRepository userRepository;
    private final MessageDBRepository messageRepository;

    /**
     * List user previous chats
     * @param myUserId current user ID
     * @return the list of user chats
     */
    public List<ChatDto> getChats(UUID myUserId) {
        return chatRepository.findByUserId(myUserId).stream()
                .map(chat -> toDto(chat, myUserId))
                .sorted(Comparator.comparing(
                        (ChatDto chat) -> chat.lastMessage().timestamp()).reversed()
                )
                .collect(Collectors.toList());
    }

    /**
     * Sends a message to a user, initializing a new chat if necessary
     * @param myUserId current user ID
     * @param contactId ID of the user to contact
     * @param messageRequest message request payload
     * @return the updated chat descriptor with the new message
     */
    @Transactional
    public ChatDto sendMessage(UUID myUserId, UUID contactId, MessageRequest messageRequest) {
        if (myUserId.equals(contactId))
            throw new SelfChatException("You can't start new chat with yourself");

        UUID user1Id = myUserId.compareTo(contactId) < 0 ? myUserId : contactId;
        UUID user2Id = myUserId.equals(user1Id) ? contactId : myUserId;
        UserDB user1 = userRepository.findById(user1Id).orElseThrow(
                () -> new UserNotFoundException("User not found")
        );
        UserDB user2 = userRepository.findById(user2Id).orElseThrow(
                () -> new UserNotFoundException("User not found")
        );

        ChatDB chat = chatRepository.findByUser1_IdAndUser2_Id(user1Id, user2Id)
                .orElseGet(() -> chatRepository.save(ChatDB.builder()
                        .user1(user1)
                        .user2(user2)
                        .build()
                ));

        MessageDB message = messageRepository.save(MessageDB.builder()
                        .chat(chat)
                        .timestamp(Instant.now())
                        .sender(user1Id.equals(myUserId) ? user1 : user2)
                        .data(Base64.getDecoder().decode(messageRequest.messageData()))
                        .iv(Base64.getDecoder().decode(messageRequest.messageIV()))
                        .build()
        );
        return toDto(chat, message, myUserId);
    }

    /**
     * List messages of a chat
     * @param myUserId current user ID
     * @param contactUserId contact user ID
     * @return the messaging history between the two users
     */
    public List<MessageDto> getMessages(UUID myUserId, UUID contactUserId) {
        UUID user1Id = myUserId.compareTo(contactUserId) < 0 ? myUserId : contactUserId;
        UUID user2Id = myUserId.equals(user1Id) ? contactUserId : myUserId;
        ChatDB chat = chatRepository.findByUser1_IdAndUser2_Id(user1Id, user2Id).orElseThrow(
                () -> new ChatNotFoundException("Chat not found")
        );
        return chat.getMessages().stream()
                .map(msg -> toDto(msg, myUserId))
                .collect(Collectors.toList());
    }

    private static ChatDto toDto(ChatDB chat, UUID userId) {
        UUID user1Id = chat.getUser1().getId(), user2Id = chat.getUser2().getId();
        return new ChatDto(
                chat.getId(),
                user1Id.equals(userId) ? user2Id : user1Id,
                toDto(chat.getMessages().getLast(), userId)
        );
    }

    private static ChatDto toDto(ChatDB chat, MessageDB message, UUID userId) {
        UUID user1Id = chat.getUser1().getId(), user2Id = chat.getUser2().getId();
        return new ChatDto(
                chat.getId(),
                user1Id.equals(userId) ? user2Id : user1Id,
                toDto(message, userId)
        );
    }

    private static MessageDto toDto(MessageDB message, UUID myUserId) {
        return new MessageDto(
                message.getTimestamp().toEpochMilli(),
                Base64.getEncoder().encodeToString(message.getData()),
                Base64.getEncoder().encodeToString(message.getIv()),
                message.getSender().getId().equals(myUserId) ?
                        MessageDto.MessageDirection.OUTGOING :
                        MessageDto.MessageDirection.INCOMING
        );
    }
}
