package it.sdc.src.service.mapping;

import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.dto.UserSessionDto;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class UserSessionMapper {
    public UserSessionDto toDto(UserSessionDB userSession) {
        return new UserSessionDto(
                userSession.getId(),
                Base64.getEncoder().encodeToString(userSession.getAccessToken()),
                userSession.getAccessTokenExpires().toEpochMilli(),
                Base64.getEncoder().encodeToString(userSession.getRefreshToken()),
                userSession.getRefreshTokenExpires().toEpochMilli()
        );
    }
}
