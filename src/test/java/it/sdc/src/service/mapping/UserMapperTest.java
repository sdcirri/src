package it.sdc.src.service.mapping;

import it.sdc.src.db.entities.UserDB;
import it.sdc.src.dto.UserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UserMapperTest {
    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        userMapper = new UserMapper();
    }

    @Test
    void toDto_shouldReturnValidMapping() {
        UUID userId = UUID.randomUUID();
        String username = "username", displayName = "displayName";
        byte[] proPic = new byte[] { 0 };

        UserDB user = mock(UserDB.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn(username);
        when(user.getDisplayName()).thenReturn(displayName);
        when(user.getProPic()).thenReturn(proPic);

        UserDto result = userMapper.toDto(user);
        assertThat(result).isEqualTo(new UserDto(
                userId,
                username,
                displayName,
                Base64.getEncoder().encodeToString(proPic)
        ));
    }

    @Test
    void toDto_shouldAllowNullProPic() {
        UUID userId = UUID.randomUUID();
        String username = "username", displayName = "displayName";

        UserDB user = mock(UserDB.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn(username);
        when(user.getDisplayName()).thenReturn(displayName);
        when(user.getProPic()).thenReturn(null);

        UserDto result = userMapper.toDto(user);
        assertThat(result).isEqualTo(new UserDto(
                userId,
                username,
                displayName,
                null
        ));
    }
}
