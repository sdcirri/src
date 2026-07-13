package it.sdc.src.service;

import it.sdc.src.db.entities.UserCryptoDB;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.repositories.UserCryptoDBRepository;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.dto.ContactCryptoDto;
import it.sdc.src.dto.UserCryptoDto;
import it.sdc.src.dto.UserDto;
import it.sdc.src.exceptions.UserNotFoundException;
import it.sdc.src.exceptions.UsernameAlreadyTakenException;
import it.sdc.src.service.mapping.UserCryptoMapper;
import it.sdc.src.service.mapping.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static it.sdc.src.test.fixtures.CryptoFixtures.mockPrivateCryptoSpecs;
import static it.sdc.src.test.fixtures.CryptoFixtures.mockPublicCryptoSpecs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class UserServiceTest {
    private UserCryptoDBRepository userCryptoRepository;
    private UserDBRepository userRepository;

    private UserCryptoMapper userCryptoMapper;
    private UserMapper userMapper;

    private ProPicNormalizer proPicNormalizer;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userCryptoRepository = mock(UserCryptoDBRepository.class);
        userRepository = mock(UserDBRepository.class);
        userCryptoMapper = mock(UserCryptoMapper.class);
        userMapper = mock(UserMapper.class);
        proPicNormalizer = mock(ProPicNormalizer.class);

        userService = new UserService(
                userCryptoRepository,
                userRepository,
                userCryptoMapper,
                userMapper,
                proPicNormalizer
        );
    }

    @Test
    void searchUsers_shouldReturnUsers() {
        when(userRepository.searchByUsername(anyString(), ArgumentMatchers.any(UUID.class), ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(mock(UserDB.class), mock(UserDB.class), mock(UserDB.class)));
        List<UserDto> result = userService.searchUsers("", 1, UUID.randomUUID());
        assertThat(result).hasSize(3);
    }

    @Test
    void getUserInfo_shouldReturnValidUser() {
        UUID userId = UUID.randomUUID();
        String username = "user", displayName = "User";

        UserDB user = mock(UserDB.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn(username);
        when(user.getDisplayName()).thenReturn(displayName);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(userMapper.toDto(ArgumentMatchers.any(UserDB.class))).thenAnswer(
                invocation -> {
                    UserDB userDB = invocation.getArgument(0);
                    return new UserDto(userDB.getId(), userDB.getUsername(), userDB.getDisplayName(), "AAAAAA==");
                }
        );

        UserDto result = userService.getUserInfo(userId);
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.username()).isEqualTo(username);
        assertThat(result.displayName()).isEqualTo(displayName);
    }

    @Test
    void getUserInfo_shouldThrowOnNonExistingUser() {
        UUID badUserId = UUID.randomUUID();
        when(userRepository.findById(badUserId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.getUserInfo(badUserId)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getMyCryptoSpecs_shouldReturnOwnCryptoSpecs() {
        UUID userId = UUID.randomUUID();
        UserCryptoDB loggedUserCrypto = mock(UserCryptoDB.class);
        when(userCryptoRepository.findById(userId)).thenReturn(Optional.of(loggedUserCrypto));
        when(userCryptoMapper.toPrivateDto(loggedUserCrypto)).thenReturn(mockPrivateCryptoSpecs(userId));

        UserCryptoDto result = userService.getMyCryptoSpecs(userId);
        assertThat(result).isEqualTo(mockPrivateCryptoSpecs(userId));
    }

    @Test
    void getMyCryptoSpecs_shouldThrowOnBadUserId() {
        UUID badUserId = UUID.randomUUID();
        when(userRepository.findById(badUserId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.getMyCryptoSpecs(badUserId)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getUserCryptoSpecs_shouldReturnPublicCryptoSpecs() {
        UUID userId = UUID.randomUUID();
        UserCryptoDB userCrypto = mock(UserCryptoDB.class);
        when(userCryptoRepository.findById(userId)).thenReturn(Optional.of(userCrypto));
        when(userCryptoMapper.toPublicDto(userCrypto)).thenReturn(mockPublicCryptoSpecs());

        ContactCryptoDto result = userService.getUserCryptoSpecs(userId);
        assertThat(result).isEqualTo(mockPublicCryptoSpecs());
    }

    @Test
    void getUserCryptoSpecs_shouldThrowOnBadUserId() {
        UUID badUserId = UUID.randomUUID();
        when(userRepository.findById(badUserId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.getUserCryptoSpecs(badUserId)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void setDisplayName_shouldSetOwnDisplayName() {
        UUID userId = UUID.randomUUID();
        String newDisplayName = "newDisplayName";
        UserDB user = mock(UserDB.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ArgumentCaptor<String> displayNameCaptor = ArgumentCaptor.forClass(String.class);
        userService.setDisplayName(userId, newDisplayName);
        verify(user).setDisplayName(displayNameCaptor.capture());
        assertThat(displayNameCaptor.getValue()).isEqualTo(newDisplayName);
    }

    @Test
    void setDisplayName_shouldThrowOnBadUserId() {
        UUID badUserId = UUID.randomUUID();
        when(userRepository.findById(badUserId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.setDisplayName(badUserId, any(String.class))).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void changeUsername_shouldSetOwnUsername() {
        UUID userId = UUID.randomUUID();
        String oldUsername = "oldUsername", newUsername = "newUsername";
        UserDB user = mock(UserDB.class);
        when(user.getUsername()).thenReturn(oldUsername);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
        userService.changeUsername(userId, newUsername);
        verify(user).setUsername(usernameCaptor.capture());
        assertThat(usernameCaptor.getValue()).isEqualTo(newUsername);
    }

    @Test
    void changeUsername_shouldThrowOnBadUserId() {
        UUID badUserId = UUID.randomUUID();
        when(userRepository.findById(badUserId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.changeUsername(badUserId, any(String.class))).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void changeUsername_shouldRejectAlreadyTakenUsername() {
        UUID myUserId = UUID.randomUUID();
        UserDB myUser = mock(UserDB.class);
        String oldUsername = "oldUsername", newUsername = "newUsername";

        when(myUser.getUsername()).thenReturn(oldUsername);
        when(userRepository.findById(myUserId)).thenReturn(Optional.of(myUser));
        doThrow(new DataIntegrityViolationException("Unique constraint violation"))
                .when(userRepository)
                .flush();

        assertThatThrownBy(() -> userService.changeUsername(myUserId, newUsername)).isInstanceOf(UsernameAlreadyTakenException.class);
    }

    @Test
    void changeUsername_idempotencyBypassesDB() {
        UUID myUserId = UUID.randomUUID();
        UserDB myUser = mock(UserDB.class);
        String oldUsername = "oldUsername", newUsername = "oldUsername";
        when(myUser.getUsername()).thenReturn(oldUsername);
        when(userRepository.findById(myUserId)).thenReturn(Optional.of(myUser));

        userService.changeUsername(myUserId, newUsername);
        verify(userRepository, never()).existsByUsername(any());
        verify(myUser, never()).setUsername(any());
    }

    @Test
    void setProPic_setsOwnProPic() throws IOException {
        UUID myUserId = UUID.randomUUID();
        UserDB myUser = mock(UserDB.class);
        byte[] propic = new byte[] {1, 2, 3, 4}, normalizedPropic =  new byte[] {5, 6, 7};

        when(proPicNormalizer.normalizeImage(propic)).thenReturn(normalizedPropic);
        when(userRepository.findById(myUserId)).thenReturn(Optional.of(myUser));

        userService.setProPic(myUserId, propic);
        verify(myUser).setProPic(normalizedPropic);
    }

    @Test
    void setProPic_shouldThrowOnBadUserId() {
        UUID badUserId = UUID.randomUUID();
        when(userRepository.findById(badUserId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.setProPic(badUserId, any(byte[].class))).isInstanceOf(UserNotFoundException.class);
    }
}
