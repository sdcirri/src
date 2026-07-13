package it.sdc.src.controllers;

import it.sdc.src.auth.UserPrincipal;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static it.sdc.src.test.fixtures.BearerAuthFixtures.mockPrincipal;
import static it.sdc.src.test.fixtures.BearerAuthFixtures.mockSession;
import static it.sdc.src.test.fixtures.UserFixtures.mockUserWithId;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class UserControllerTest {
    @Mock
    UserService userService;

    @Mock
    MultipartFile image;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    UserController userController;

    @Test
    void setProPic_shouldHandleMemoryIOErrors() throws IOException {
        when(passwordEncoder.encode(any())).thenReturn("hash");
        UserSessionDB mockSession = mockSession(mockUserWithId(passwordEncoder, 1));
        UserPrincipal principal = mockPrincipal(mockSession);
        when(userService.setProPic(any(), any())).thenThrow(new IOException("broken stream"));

        assertThatThrownBy(() -> userController.setProPic(principal, image))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(UserController.PROPIC_IO_ERROR_MSG);
    }
}
