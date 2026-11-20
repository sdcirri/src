package it.sdc.src.service;

import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserCryptoDBRepository;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import it.sdc.src.dto.requests.UserRegistrationFinalizationRequest;
import it.sdc.src.dto.requests.UserRegistrationRequest;
import it.sdc.src.dto.requests.accountedits.PasswordChangeRequest;
import it.sdc.src.exceptions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

public class AuthServiceTest {
    private PasswordEncoder passwordEncoder;

    private UserSessionDBRepository userSessionRepository;
    private UserCryptoDBRepository userCryptoRepository;
    private UserDBRepository userRepository;
    private SessionService sessionService;
    private AccountService accountService;
    private HibpClient hibpClient;

    private AuthService authService;

    private static String b64(byte... bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private UserRegistrationFinalizationRequest validFinalizationRequest() {
        return new UserRegistrationFinalizationRequest(
                "password",
                b64((byte) 1, (byte) 2, (byte) 3),    // kekSalt
                b64((byte) 10, (byte) 11),            // privateEd25519Crypto
                b64((byte) 20, (byte) 21),            // privateEd25519IV
                b64((byte) 30, (byte) 31),            // publicEd25519
                b64((byte) 40, (byte) 41),            // privateX25519Crypto
                b64((byte) 50, (byte) 51),            // privateX25519IV
                b64((byte) 60, (byte) 61)             // publicX25519
        );
    }

    private PasswordChangeRequest validPasswordChangeRequest(String oldPassword, String newPassword) {
        return new PasswordChangeRequest(
                oldPassword,
                newPassword,
                b64((byte) 1, (byte) 2, (byte) 3),    // kekSalt
                b64((byte) 10, (byte) 11),            // privateEd25519Crypto
                b64((byte) 20, (byte) 21),            // privateEd25519IV
                b64((byte) 40, (byte) 41),            // privateX25519Crypto
                b64((byte) 50, (byte) 51)             // privateX25519IV
        );
    }

    @BeforeEach
    void setUp() {
        passwordEncoder = mock(Argon2PasswordEncoder.class);

        userSessionRepository = mock(UserSessionDBRepository.class);
        userCryptoRepository = mock(UserCryptoDBRepository.class);
        userRepository = mock(UserDBRepository.class);
        sessionService = mock(SessionService.class);
        accountService = mock(AccountService.class);
        hibpClient = mock(HibpClient.class);

        authService = new AuthService(
                passwordEncoder,
                userCryptoRepository,
                userRepository,
                sessionService,
                accountService,
                hibpClient
        );
    }

    @BeforeEach
    void setUpTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void login_shouldYieldAValidSessionOnGoodCredentials() {
        UserDB user = mock(UserDB.class);
        when(user.getPasswordHash()).thenReturn("passwordHash");

        when(userSessionRepository.save(any(UserSessionDB.class))).thenAnswer(invocation -> {
            UserSessionDB session = invocation.getArgument(0);
            return UserSessionDB.builder()
                    .id(UUID.randomUUID())
                    .user(session.getUser())
                    .accessToken(session.getAccessToken())
                    .accessTokenExpires(session.getAccessTokenExpires())
                    .refreshToken(session.getRefreshToken())
                    .refreshTokenExpires(session.getRefreshTokenExpires())
                    .build();
        });

        when(userRepository.findByUsername("username")).thenReturn(Optional.of(user));

        when(passwordEncoder.encode("newPassword")).thenReturn("passwordHash");
        when(passwordEncoder.matches("newPassword", "passwordHash")).thenReturn(true);

        authService.login("username", "newPassword");

        verify(sessionService).yieldSession(user);
    }

    @Test
    void login_shouldFailOnBadCredentials() {
        UserDB user = mock(UserDB.class);
        when(user.getPasswordHash()).thenReturn("passwordHash");

        when(userRepository.findByUsername("username")).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("badusername")).thenReturn(Optional.empty());

        when(passwordEncoder.encode("newPassword")).thenReturn("passwordHash");
        when(passwordEncoder.matches("newPassword", "passwordHash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login("badusername", "newPassword"))
                .isInstanceOf(LoginFailedException.class)
                .hasMessage("Invalid username")
        ;

        assertThatThrownBy(() -> authService.login("username", "badpassword"))
                .isInstanceOf(LoginFailedException.class)
                .hasMessage("Invalid password")
        ;
    }

    @Test
    void register_shouldInitializeRegistration() {
        String username = "username", displayName = "displayName", password = "newPassword";
        when(passwordEncoder.encode(password)).thenReturn("passwordHash");
        when(passwordEncoder.matches(password, "passwordHash")).thenReturn(true);
        when(hibpClient.isPwned(password)).thenReturn(HibpClient.HibpResult.NOT_PWNED);

        authService.register(new UserRegistrationRequest(username, displayName, password));

        verify(accountService).registerUser(username, displayName, "passwordHash");
    }

    @Test
    void register_shouldRejectPwnedPassword() {
        when(hibpClient.isPwned(anyString())).thenReturn(HibpClient.HibpResult.PWNED);

        UserRegistrationRequest request = new UserRegistrationRequest("username", "Display Name", "newPassword");

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(PwnedPasswordException.class);
    }

    @Test
    void finalizeRegistration_shouldPersistCryptoSpecs() {
        UUID userId = UUID.randomUUID();
        UserRegistrationFinalizationRequest request = validFinalizationRequest();

        UserDB user = mock(UserDB.class);
        when(user.getId()).thenReturn(userId);
        when(user.getPasswordHash()).thenReturn("passwordHash");
        when(passwordEncoder.matches("password", "passwordHash")).thenReturn(true);

        when(userCryptoRepository.existsById(userId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        authService.finalizeRegistration(userId, request);

        Base64.Decoder decoder = Base64.getDecoder();
        verify(accountService).bootstrapUserCrypto(
                userId,
                decoder.decode(request.kekSalt()),
                decoder.decode(request.privateEd25519Crypto()),
                decoder.decode(request.privateEd25519IV()),
                decoder.decode(request.publicEd25519()),
                decoder.decode(request.privateX25519Crypto()),
                decoder.decode(request.privateX25519IV()),
                decoder.decode(request.publicX25519())
        );
    }

    @Test
    void finalizeRegistration_shouldFailIfCryptoAlreadyExists() {
        UUID userId = UUID.randomUUID();
        when(userCryptoRepository.existsById(userId)).thenReturn(true);

        assertThatThrownBy(() -> authService.finalizeRegistration(userId, validFinalizationRequest()))
                .isInstanceOf(CryptoConflictException.class)
                .hasMessage("Attempt to re-init user crypto");

        verify(userRepository, never()).findById(any());
        verify(userCryptoRepository, never()).save(any());
    }

    @Test
    void finalizeRegistration_shouldFailIfUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userCryptoRepository.existsById(userId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.finalizeRegistration(userId, validFinalizationRequest()))
                .isInstanceOf(LoginFailedException.class)
                .hasMessage("Bad request");

        verify(userCryptoRepository, never()).save(any());
    }

    @Test
    void finalizeRegistration_shouldFailIfPasswordMismatch() {
        UUID userId = UUID.randomUUID();
        UserRegistrationFinalizationRequest request = validFinalizationRequest();

        UserDB user = mock(UserDB.class);
        when(user.getId()).thenReturn(userId);
        when(user.getPasswordHash()).thenReturn("passwordHash");
        when(passwordEncoder.matches("password", "passwordHash")).thenReturn(false);

        when(userCryptoRepository.existsById(userId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(
                () -> authService.finalizeRegistration(userId, request)
        ).isInstanceOf(BadPasswordException.class);
    }

    @Test
    void changePassword_shouldChangePassword() {
        UUID userId = UUID.randomUUID();
        UserDB user = mock(UserDB.class);
        String oldPassword = "oldPassword", newPassword = "newPassword";
        when(user.getPasswordHash()).thenReturn("oldPasswordHash");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(hibpClient.isPwned(newPassword)).thenReturn(HibpClient.HibpResult.NOT_PWNED);
        when(passwordEncoder.encode(oldPassword)).thenReturn("oldPasswordHash");
        when(passwordEncoder.encode(newPassword)).thenReturn("newPasswordHash");
        when(passwordEncoder.matches(oldPassword, "oldPasswordHash")).thenReturn(true);
        when(passwordEncoder.matches(newPassword, "newPasswordHash")).thenReturn(true);

        PasswordChangeRequest request = validPasswordChangeRequest(oldPassword, newPassword);
        authService.changePassword(userId, request);

        Base64.Decoder decoder = Base64.getDecoder();
        verify(accountService).changeUserPassword(
                userId,
                passwordEncoder.encode(request.newPassword()),
                decoder.decode(request.newKekSalt()),
                decoder.decode(request.newPrivateEd25519()),
                decoder.decode(request.newIvEd25519()),
                decoder.decode(request.newPrivateX25519()),
                decoder.decode(request.newIvX25519())
        );
    }

    @Test
    void changePassword_shouldFailIfUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(
                () -> authService.changePassword(userId, validPasswordChangeRequest("old", "new"))
        ).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void changePassword_shouldFailIfOldPasswordDoesNotMatch() {
        UUID userId = UUID.randomUUID();
        UserDB user = mock(UserDB.class);
        String oldPassword = "oldPassword", newPassword = "newPassword";
        when(user.getId()).thenReturn(userId);
        when(user.getPasswordHash()).thenReturn("oldPasswordHash");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(passwordEncoder.encode(oldPassword)).thenReturn("oldPasswordHash");
        when(passwordEncoder.encode(newPassword)).thenReturn("newPasswordHash");
        when(passwordEncoder.matches(oldPassword, "oldPasswordHash")).thenReturn(false);
        when(passwordEncoder.matches(newPassword, "newPasswordHash")).thenReturn(true);

        assertThatThrownBy(
                () -> authService.changePassword(userId, validPasswordChangeRequest(oldPassword, newPassword))
        ).isInstanceOf(BadPasswordException.class);
    }

    @Test
    void changePassword_shouldFailIfPasswordIdentical() {
        UUID userId = UUID.randomUUID();
        UserDB user = mock(UserDB.class);
        String oldPassword = "oldPassword";
        when(user.getId()).thenReturn(userId);
        when(user.getPasswordHash()).thenReturn("oldPasswordHash");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(passwordEncoder.encode(oldPassword)).thenReturn("oldPasswordHash");
        when(passwordEncoder.matches(oldPassword, "oldPasswordHash")).thenReturn(true);

        assertThatThrownBy(
                () -> authService.changePassword(userId, validPasswordChangeRequest(oldPassword, oldPassword))
        ).isInstanceOf(PasswordConflictException.class);
    }

    @Test
    void changePassword_shouldRejectPwnedPassword() {
        UUID userId = UUID.randomUUID();
        UserDB user = mock(UserDB.class);
        String oldPassword = "oldPassword", newPassword = "newPassword";
        when(user.getId()).thenReturn(userId);
        when(user.getPasswordHash()).thenReturn("oldPasswordHash");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(passwordEncoder.matches(oldPassword, "oldPasswordHash")).thenReturn(true);
        when(passwordEncoder.matches(newPassword, "oldPasswordHash")).thenReturn(false);

        when(hibpClient.isPwned(anyString())).thenReturn(HibpClient.HibpResult.PWNED);

        assertThatThrownBy(
                () -> authService.changePassword(userId, validPasswordChangeRequest(oldPassword, newPassword))
        ).isInstanceOf(PwnedPasswordException.class);
    }
}
