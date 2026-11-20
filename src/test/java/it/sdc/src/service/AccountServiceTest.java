package it.sdc.src.service;

import it.sdc.src.db.entities.UserCryptoDB;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.repositories.UserCryptoDBRepository;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.dto.UserCryptoDto;
import it.sdc.src.dto.UserSessionDto;
import it.sdc.src.exceptions.UserNotFoundException;
import it.sdc.src.exceptions.UsernameAlreadyTakenException;
import it.sdc.src.service.mapping.UserCryptoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static it.sdc.src.test.fixtures.CryptoFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.*;

public class AccountServiceTest {
    private UserCryptoDBRepository userCryptoRepository;
    private UserDBRepository userRepository;

    private UserCryptoMapper userCryptoMapper;

    private SessionService sessionService;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        userCryptoRepository = mock(UserCryptoDBRepository.class);
        userRepository = mock(UserDBRepository.class);
        userCryptoMapper = mock(UserCryptoMapper.class);
        sessionService = mock(SessionService.class);

        accountService = new AccountService(userCryptoRepository, userRepository, userCryptoMapper, sessionService);
    }

    @Test
    void registerUser_shouldPersistUserAndYieldSession() {
        String username = "user", displayName = "The User", passwordHash = "hash";

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.save(any(UserDB.class))).then(returnsFirstArg());

        ArgumentCaptor<UserDB> captor = ArgumentCaptor.forClass(UserDB.class), yieldCaptor = ArgumentCaptor.forClass(UserDB.class);

        UserSessionDto expected = mock(UserSessionDto.class);
        when(sessionService.yieldSession(any())).thenReturn(expected);

        UserSessionDto result = accountService.registerUser(username, displayName, passwordHash);

        verify(userRepository).save(captor.capture());
        verify(sessionService).yieldSession(yieldCaptor.capture());

        assertThat(result).isSameAs(expected);

        UserDB user = captor.getValue();
        assertThat(user.getUsername()).isEqualTo(username);
        assertThat(user.getDisplayName()).isEqualTo(displayName);
        assertThat(user.getPasswordHash()).isEqualTo(passwordHash);
        assertThat(user.getRegistrationTimeUTC()).isNotNull();
        assertThat(user).isSameAs(yieldCaptor.getValue());
    }

    @Test
    void registerUser_displayNameIsNullable() {
        String username = "user", passwordHash = "hash";

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.save(any(UserDB.class))).then(returnsFirstArg());

        ArgumentCaptor<UserDB> captor = ArgumentCaptor.forClass(UserDB.class);

        accountService.registerUser(username, null, passwordHash);

        verify(userRepository).save(captor.capture());
        UserDB user = captor.getValue();
        assertThat(user.getUsername()).isEqualTo(username);
        assertThat(user.getDisplayName()).isNull();
        assertThat(user.getPasswordHash()).isEqualTo(passwordHash);
    }

    @Test
    void registerUser_throwsOnUsernameAlreadyTaken() {
        String username = "user";

        when(userRepository.existsByUsername(username)).thenReturn(true);

        assertThatThrownBy(
                () -> accountService.registerUser(username, "a", "a")
        ).isInstanceOf(UsernameAlreadyTakenException.class);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(sessionService);
    }

    @Test
    void bootstrapUserCrypto_persistsSpecsCorrectly() {
        UUID userId = UUID.randomUUID();
        UserDB user = mock(UserDB.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userCryptoRepository.save(any(UserCryptoDB.class))).then(returnsFirstArg());

        ArgumentCaptor<UserCryptoDB> captor = ArgumentCaptor.forClass(UserCryptoDB.class), mapCaptor = ArgumentCaptor.forClass(UserCryptoDB.class);

        UserCryptoDto expected = mock(UserCryptoDto.class);

        when(userCryptoMapper.toPrivateDto(any())).thenReturn(expected);

        UserCryptoDto result = accountService.bootstrapUserCrypto(
                userId, KEK_SALT,
                PRIVATE_ED25519_CRYPTO, PRIVATE_ED25519_IV, PUBLIC_ED25519,
                PRIVATE_X25519_CRYPTO, PRIVATE_X25519_IV, PUBLIC_X25519
        );

        verify(userCryptoRepository).save(captor.capture());
        verify(userCryptoMapper).toPrivateDto(mapCaptor.capture());

        assertThat(result).isSameAs(expected);

        UserCryptoDB userCrypto = captor.getValue();
        assertThat(userCrypto).isSameAs(mapCaptor.getValue());
        assertThat(userCrypto.getUserDB()).isSameAs(user);
        assertThat(userCrypto.getKekSalt()).isEqualTo(KEK_SALT);
        assertThat(userCrypto.getPrivateEd25519()).isEqualTo(PRIVATE_ED25519_CRYPTO);
        assertThat(userCrypto.getIvEd25519()).isEqualTo(PRIVATE_ED25519_IV);
        assertThat(userCrypto.getPublicEd25519()).isEqualTo(PUBLIC_ED25519);
        assertThat(userCrypto.getPrivateX25519()).isEqualTo(PRIVATE_X25519_CRYPTO);
        assertThat(userCrypto.getIvX25519()).isEqualTo(PRIVATE_X25519_IV);
        assertThat(userCrypto.getPublicX25519()).isEqualTo(PUBLIC_X25519);
    }

    @Test
    void bootstrapUserCrypto_throwsOnUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.bootstrapUserCrypto(
                userId, KEK_SALT,
                PRIVATE_ED25519_CRYPTO, PRIVATE_ED25519_IV, PUBLIC_ED25519,
                PRIVATE_X25519_CRYPTO, PRIVATE_X25519_IV, PUBLIC_X25519
        )).isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(userCryptoRepository);
        verifyNoInteractions(userCryptoMapper);
    }

    @Test
    void changeUserPassword_updatesUserPasswordAndCrypto() {
        UUID userId = UUID.randomUUID();
        UserDB user = mock(UserDB.class);

        UserCryptoDB userCrypto = mock(UserCryptoDB.class);
        when(user.getCrypto()).thenReturn(userCrypto);

        String newPasswordHash = "hash";

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        accountService.changeUserPassword(
                userId, newPasswordHash, KEK_SALT,
                PRIVATE_ED25519_CRYPTO, PRIVATE_ED25519_IV,
                PRIVATE_X25519_CRYPTO, PRIVATE_X25519_IV
        );

        verify(userRepository).save(user);

        verify(user).setPasswordHash(newPasswordHash);
        verify(userCrypto).setKekSalt(KEK_SALT);
        verify(userCrypto).setPrivateEd25519(PRIVATE_ED25519_CRYPTO);
        verify(userCrypto).setIvEd25519(PRIVATE_ED25519_IV);
        verify(userCrypto).setPrivateX25519(PRIVATE_X25519_CRYPTO);
        verify(userCrypto).setIvX25519(PRIVATE_X25519_IV);
    }

    @Test
    void changeUserPassword_throwsOnUserNotFound() {
        UUID userId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.changeUserPassword(
                userId, "hash", KEK_SALT,
                PRIVATE_ED25519_CRYPTO, PRIVATE_ED25519_IV,
                PRIVATE_X25519_CRYPTO, PRIVATE_X25519_IV
        )).isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(userCryptoRepository);
    }
}
