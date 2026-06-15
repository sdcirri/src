package it.sdc.src.service;

import it.sdc.src.db.entities.ChatDB;
import it.sdc.src.db.entities.MessageDB;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.repositories.ChatDBRepository;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.dto.ChatDto;
import it.sdc.src.dto.MessageDto;
import it.sdc.src.exceptions.ChatNotFoundException;
import it.sdc.src.exceptions.SelfChatException;
import it.sdc.src.exceptions.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatDBRepository chatRepository;
    private final UserDBRepository userRepository;

    public ChatDto startNewChat(UUID myUserId, UUID contactUserId) {
        if (myUserId.equals(contactUserId))
            throw new SelfChatException("You can't start new chat with yourself");
        UUID user1Id = myUserId.compareTo(contactUserId) < 0 ? myUserId : contactUserId;
        UUID user2Id = myUserId.equals(user1Id) ? contactUserId : myUserId;

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

        byte[] ed25519 = myUserId.equals(user1Id) ? user2.getCrypto().getPublicEd25519() : user1.getCrypto().getPublicEd25519();
        byte[] x25519 = myUserId.equals(user1Id) ? user2.getCrypto().getPublicX25519() : user1.getCrypto().getPublicX25519();

        return new ChatDto(
                chat.getId(),
                Base64.getEncoder().encodeToString(ed25519),
                Base64.getEncoder().encodeToString(x25519)
        );
    }

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
