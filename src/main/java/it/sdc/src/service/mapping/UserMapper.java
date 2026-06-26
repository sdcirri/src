package it.sdc.src.service.mapping;

import it.sdc.src.db.entities.UserDB;
import it.sdc.src.dto.UserDto;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class UserMapper {
    public UserDto toDto(UserDB user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                // TODO: load default propic when not set
                Base64.getEncoder().encodeToString(user.getProPic())
        );
    }
}
