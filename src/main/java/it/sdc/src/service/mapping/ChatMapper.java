package it.sdc.src.service.mapping;

import it.sdc.src.db.entities.ChatDB;
import it.sdc.src.dto.ChatDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatMapper {
    private final MessageMapper messageMapper;

    public ChatDto toDto(ChatDB chat, UUID userId) {
        UUID user1Id = chat.getUser1().getId(), user2Id = chat.getUser2().getId();
        return new ChatDto(
                chat.getId(),
                user1Id.equals(userId) ? user2Id : user1Id,
                // chats always have at least one message
                messageMapper.toDto(chat.getMessages().getLast(), userId)
        );
    }
}
