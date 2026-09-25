package it.sdc.src.service.mapping;

import it.sdc.src.db.entities.MessageDB;
import it.sdc.src.dto.MessageDto;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.UUID;

@Component
public class MessageMapper {

    public MessageDto toDto(MessageDB message, MessageDto.MessageDirection direction) {
        return new MessageDto(
                message.getSender().getId(),
                message.getTimestamp().toEpochMilli(),
                Base64.getEncoder().encodeToString(message.getData()),
                Base64.getEncoder().encodeToString(message.getIv()),
                direction
        );
    }

    public MessageDto toDto(MessageDB message, UUID myUserId) {
        return toDto(message, message.getSender().getId().equals(myUserId) ?
                MessageDto.MessageDirection.OUTGOING :
                MessageDto.MessageDirection.INCOMING
        );
    }
}
