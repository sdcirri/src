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
                // let the frontend manage users with no propic set
                user.getProPic() == null ? null : Base64.getEncoder().encodeToString(user.getProPic())
        );
    }
}
