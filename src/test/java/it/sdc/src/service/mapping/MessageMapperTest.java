package it.sdc.src.service.mapping;

import it.sdc.src.db.entities.ChatDB;
import it.sdc.src.db.entities.MessageDB;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.dto.MessageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MessageMapperTest {
    private MessageMapper messageMapper;

    @BeforeEach
    void setUp() {
        messageMapper = new MessageMapper();
    }

    @Test
    void toDto_mapsMessageCorrectly() {
        UUID user1Id = UUID.randomUUID(), user2Id = UUID.randomUUID();
        UserDB user1DB = mock(UserDB.class), user2DB = mock(UserDB.class);
        when(user1DB.getId()).thenReturn(user1Id);
        when(user2DB.getId()).thenReturn(user2Id);

        UUID chatId = UUID.randomUUID();
        ChatDB chatDB = mock(ChatDB.class);
        when(chatDB.getId()).thenReturn(chatId);
        when(chatDB.getUser1()).thenReturn(user1DB);
        when(chatDB.getUser2()).thenReturn(user2DB);

        byte[] messageData = new byte[] {1, 2, 3, 4}, messageIv = new byte[] {5, 6};
        Instant messageTimestamp = Instant.now();

        UUID messageId = UUID.randomUUID();
        MessageDB messageDB = mock(MessageDB.class);
        when(messageDB.getId()).thenReturn(messageId);
        when(messageDB.getChat()).thenReturn(chatDB);
        when(messageDB.getData()).thenReturn(messageData);
        when(messageDB.getIv()).thenReturn(messageIv);
        when(messageDB.getTimestamp()).thenReturn(messageTimestamp);
        when(messageDB.getSender()).thenReturn(user1DB);

        MessageDto result = messageMapper.toDto(messageDB, user1Id);
        assertThat(result).isEqualTo(new MessageDto(
                user1Id,
                chatId,
                messageTimestamp.toEpochMilli(),
                Base64.getEncoder().encodeToString(messageData),
                Base64.getEncoder().encodeToString(messageIv),
                MessageDto.MessageDirection.OUTGOING
        ));

        result = messageMapper.toDto(messageDB, user2Id);
        assertThat(result).isEqualTo(new MessageDto(
                user1Id,
                chatId,
                messageTimestamp.toEpochMilli(),
                Base64.getEncoder().encodeToString(messageData),
                Base64.getEncoder().encodeToString(messageIv),
                MessageDto.MessageDirection.INCOMING
        ));
    }
}
