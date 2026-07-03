package it.sdc.src.service.mapping;

import it.sdc.src.db.entities.ChatDB;
import it.sdc.src.db.entities.MessageDB;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.dto.ChatDto;
import it.sdc.src.dto.MessageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ChatMapperTest {
    private ChatMapper chatMapper;
    private MessageMapper messageMapper;

    @BeforeEach
    public void setUp() {
        messageMapper = mock(MessageMapper.class);
        chatMapper = new ChatMapper(messageMapper);
    }

    @Test
    public void toDto_mapsChatDBCorrectly() {
        UUID user1Id = UUID.randomUUID(), user2Id = UUID.randomUUID(), chatId = UUID.randomUUID();
        UserDB user1DB = mock(UserDB.class), user2DB = mock(UserDB.class);
        when(user1DB.getId()).thenReturn(user1Id);
        when(user2DB.getId()).thenReturn(user2Id);

        ChatDB chatDB = mock(ChatDB.class);
        when(chatDB.getId()).thenReturn(chatId);
        when(chatDB.getUser1()).thenReturn(user1DB);
        when(chatDB.getUser2()).thenReturn(user2DB);

        MessageDB lastMessageDB = mock(MessageDB.class);
        MessageDto lastMessage = mock(MessageDto.class);
        when(chatDB.getMessages()).thenReturn(List.of(lastMessageDB));
        when(messageMapper.toDto(eq(lastMessageDB), any(UUID.class))).thenReturn(lastMessage);

        ChatDto result = chatMapper.toDto(chatDB, user1Id);
        assertThat(result).isEqualTo(new ChatDto(chatId, user2Id, lastMessage));
        verify(messageMapper).toDto(lastMessageDB, user1Id);

        result = chatMapper.toDto(chatDB, user2Id);
        assertThat(result).isEqualTo(new ChatDto(chatId, user1Id, lastMessage));
        verify(messageMapper).toDto(lastMessageDB, user2Id);
    }

    @Test
    public void toDto_shouldThrowOnBadUserId() {
        UUID user1Id =  UUID.randomUUID(), user2Id = UUID.randomUUID(), nosyUserId = UUID.randomUUID();
        UserDB user1DB = mock(UserDB.class), user2DB = mock(UserDB.class);
        when(user1DB.getId()).thenReturn(user1Id);
        when(user2DB.getId()).thenReturn(user2Id);

        ChatDB chatDB = mock(ChatDB.class);
        when(chatDB.getUser1()).thenReturn(user1DB);
        when(chatDB.getUser2()).thenReturn(user2DB);

        assertThatThrownBy(() -> chatMapper.toDto(chatDB, nosyUserId)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void toDto_shouldThrowOnEmptyChat() {
        UUID user1Id =  UUID.randomUUID(), user2Id = UUID.randomUUID();
        UserDB user1DB = mock(UserDB.class), user2DB = mock(UserDB.class);
        when(user1DB.getId()).thenReturn(user1Id);
        when(user2DB.getId()).thenReturn(user2Id);

        ChatDB chatDB = mock(ChatDB.class);
        when(chatDB.getUser1()).thenReturn(user1DB);
        when(chatDB.getUser2()).thenReturn(user2DB);
        when(chatDB.getMessages()).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> chatMapper.toDto(chatDB, user1Id)).isInstanceOf(IllegalArgumentException.class);
    }
}
