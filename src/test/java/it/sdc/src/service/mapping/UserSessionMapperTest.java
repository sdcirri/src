package it.sdc.src.service.mapping;

import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.dto.UserSessionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UserSessionMapperTest {
    private UserSessionMapper userSessionMapper;

    @BeforeEach
    public void setUp() {
        userSessionMapper = new UserSessionMapper();
    }

    @Test
    void toDto_mapsSessionCorrectly() {
        UUID userSessionId = UUID.randomUUID();
        byte[] accessToken = new byte[] {1, 2, 3, 4}, refreshToken = new byte[] {5, 6, 7, 8};
        Instant accessTokenExpires = Instant.now(), refreshTokenExpires = Instant.now();

        UserSessionDB userSessionDB = mock(UserSessionDB.class);
        when(userSessionDB.getId()).thenReturn(userSessionId);
        when(userSessionDB.getAccessToken()).thenReturn(accessToken);
        when(userSessionDB.getAccessTokenExpires()).thenReturn(accessTokenExpires);
        when(userSessionDB.getRefreshToken()).thenReturn(refreshToken);
        when(userSessionDB.getRefreshTokenExpires()).thenReturn(refreshTokenExpires);

        UserSessionDto result = userSessionMapper.toDto(userSessionDB);
        assertThat(result).isEqualTo(new UserSessionDto(
                userSessionId,
                Base64.getEncoder().encodeToString(accessToken),
                accessTokenExpires.toEpochMilli(),
                Base64.getEncoder().encodeToString(refreshToken),
                refreshTokenExpires.toEpochMilli()
        ));
    }
}
