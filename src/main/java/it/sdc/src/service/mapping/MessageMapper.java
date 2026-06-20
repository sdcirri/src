package it.sdc.src.service.mapping;

import it.sdc.src.db.entities.MessageDB;
import it.sdc.src.dto.MessageDto;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.UUID;

@Component
public class MessageMapper {
    public MessageDto toDto(MessageDB message, UUID myUserId) {
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
