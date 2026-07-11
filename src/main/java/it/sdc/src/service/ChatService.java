package it.sdc.src.service;

import it.sdc.src.db.entities.ChatDB;
import it.sdc.src.db.entities.MessageDB;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.repositories.ChatDBRepository;
import it.sdc.src.db.repositories.MessageDBRepository;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.dto.ChatDto;
import it.sdc.src.dto.MessageDto;
import it.sdc.src.dto.requests.MessageRequest;
import it.sdc.src.exceptions.ChatNotFoundException;
import it.sdc.src.exceptions.SelfChatException;
import it.sdc.src.exceptions.UserNotFoundException;
import it.sdc.src.service.mapping.ChatMapper;
import it.sdc.src.service.mapping.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatDBRepository chatRepository;
    private final UserDBRepository userRepository;
    private final MessageDBRepository messageRepository;

    private final ChatMapper chatMapper;
    private final MessageMapper messageMapper;

    /**
     * List user previous chats
     * @param myUserId current user ID
     * @return the list of user chats
     */
    @Transactional(readOnly = true)
    public List<ChatDto> getChats(UUID myUserId) {
        return chatRepository.findByUserIdWithMessages(myUserId).stream()
                .map(chat -> chatMapper.toDto(chat, myUserId))
                .sorted(Comparator.comparing(
                        (ChatDto chat) -> chat.lastMessage().timestamp()).reversed()
                )
                .toList();
    }

    /**
     * Sends a message to a user, initializing a new chat if necessary
     * @param myUserId current user ID
     * @param contactId ID of the user to contact
     * @param messageRequest message request payload
     * @return the new message
     */
    @Transactional
    public MessageDto sendMessage(UUID myUserId, UUID contactId, MessageRequest messageRequest) {
        if (myUserId.equals(contactId))
            throw new SelfChatException("You can't start new chat with yourself");

        // Postgres ordering is different from UUID.compareTo(...)
        UUID user1Id = myUserId.toString().compareTo(contactId.toString()) < 0 ? myUserId : contactId;

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
        return messageMapper.toDto(message, myUserId);
    }

    /**
     * List messages of a chat
     * @param myUserId current user ID
     * @param contactUserId contact user ID
     * @return the messaging history between the two users
     */
    public List<MessageDto> getMessages(UUID myUserId, UUID contactUserId) {
        UUID user1Id = myUserId.toString().compareTo(contactUserId.toString()) < 0 ? myUserId : contactUserId;
        UUID user2Id = myUserId.toString().equals(user1Id.toString()) ? contactUserId : myUserId;
        ChatDB chat = chatRepository.findByUser1_IdAndUser2_Id(user1Id, user2Id).orElseThrow(
                () -> new ChatNotFoundException("Chat not found")
        );
        return chat.getMessages().stream()
                .map(msg -> messageMapper.toDto(msg, myUserId))
                .toList();
    }
}
