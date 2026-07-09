package it.sdc.src.service;

import it.sdc.src.auth.TokenIntrospectionCache;
import it.sdc.src.config.AuthProperties;
import it.sdc.src.db.entities.UserCryptoDB;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserCryptoDBRepository;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import it.sdc.src.dto.UserCryptoDto;
import it.sdc.src.dto.UserSessionDto;
import it.sdc.src.dto.requests.UserRegistrationFinalizationRequest;
import it.sdc.src.dto.requests.UserRegistrationRequest;
import it.sdc.src.dto.requests.accountedits.PasswordChangeRequest;
import it.sdc.src.exceptions.*;
import it.sdc.src.service.mapping.UserCryptoMapper;
import it.sdc.src.service.mapping.UserSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

public class AuthServiceTest {
    private PasswordEncoder passwordEncoder;
    private SecureRandom secureRandom;

    private UserSessionMapper userSessionMapper;
    private UserCryptoMapper userCryptoMapper;

    private UserSessionDBRepository userSessionRepository;
    private UserCryptoDBRepository userCryptoRepository;
    private UserDBRepository userRepository;
    private TokenIntrospectionCache tokenIntrospectionCache;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = mock(Argon2PasswordEncoder.class);
        secureRandom = new SecureRandom();

        userSessionMapper = new UserSessionMapper();
        userCryptoMapper = new UserCryptoMapper();

        userSessionRepository = mock(UserSessionDBRepository.class);
        userCryptoRepository = mock(UserCryptoDBRepository.class);
        userRepository = mock(UserDBRepository.class);
        tokenIntrospectionCache = mock(TokenIntrospectionCache.class);

        AuthProperties authProperties = new AuthProperties();
        authProperties.setAccessTokenValiditySeconds(3600);
        authProperties.setRefreshTokenValiditySeconds(3600);

        authService = new AuthService(
                passwordEncoder,
                secureRandom,
                userSessionRepository,
                userCryptoRepository,
                userRepository,
                userSessionMapper,
                userCryptoMapper,
                tokenIntrospectionCache,
                authProperties
        );
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

        when(passwordEncoder.encode("password")).thenReturn("passwordHash");
        when(passwordEncoder.matches("password", "passwordHash")).thenReturn(true);

        UserSessionDto result = authService.login("username", "password");
        ArgumentCaptor<UserSessionDB> captor = ArgumentCaptor.forClass(UserSessionDB.class);
        verify(userSessionRepository).save(captor.capture());

        UserSessionDB saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getAccessToken()).hasSize(32);
        assertThat(saved.getRefreshToken()).hasSize(32);
        assertThat(saved.getAccessTokenExpires()).isAfter(Instant.now());

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
    }

    @Test
    void login_shouldFailOnBadCredentials() {
        UserDB user = mock(UserDB.class);
        when(user.getPasswordHash()).thenReturn("passwordHash");

        when(userRepository.findByUsername("username")).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("badusername")).thenReturn(Optional.empty());

        when(passwordEncoder.encode("password")).thenReturn("passwordHash");
        when(passwordEncoder.matches("password", "passwordHash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login("badusername", "password"))
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
        when(passwordEncoder.encode("password")).thenReturn("passwordHash");
        when(passwordEncoder.matches("password", "passwordHash")).thenReturn(true);

        when(userRepository.save(any(UserDB.class))).thenAnswer(invocation -> {
            UserDB user = invocation.getArgument(0);
            return UserDB.builder()
                    .id(UUID.randomUUID())
                    .username(user.getUsername())
                    .displayName(user.getDisplayName())
                    .passwordHash(user.getPasswordHash())
                    .registrationTimeUTC(user.getRegistrationTimeUTC())
                    .build();
        });
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

        UserRegistrationRequest request = new UserRegistrationRequest("username", "Display Name", "password");
        UserSessionDto result = authService.register(request);

        ArgumentCaptor<UserDB> userCaptor = ArgumentCaptor.forClass(UserDB.class);
        verify(userRepository).save(userCaptor.capture());

        UserDB savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("username");
        assertThat(savedUser.getDisplayName()).isEqualTo("Display Name");
        assertThat(savedUser.getPasswordHash()).isEqualTo("passwordHash");
        assertThat(savedUser.getRegistrationTimeUTC()).isBeforeOrEqualTo(Instant.now());

        ArgumentCaptor<UserSessionDB> sessionCaptor = ArgumentCaptor.forClass(UserSessionDB.class);
        verify(userSessionRepository).save(sessionCaptor.capture());

        UserSessionDB savedSession = sessionCaptor.getValue();
        assertThat(savedSession.getUser().getUsername()).isEqualTo(savedUser.getUsername());
        assertThat(savedSession.getUser().getPasswordHash()).isEqualTo(savedUser.getPasswordHash());
        assertThat(savedSession.getUser().getDisplayName()).isEqualTo(savedUser.getDisplayName());

        assertThat(savedSession.getAccessToken()).hasSize(32);
        assertThat(savedSession.getRefreshToken()).hasSize(32);

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();

        verify(passwordEncoder).encode("password");
        verify(userRepository, never()).findByUsername(any());
    }

    @Test
    void register_shouldFailIfUsernameAlreadyTaken() {
        when(userRepository.existsByUsername("username")).thenReturn(true);

        UserRegistrationRequest request = new UserRegistrationRequest("username", "Display Name", "password");

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(UsernameAlreadyTakenException.class);
    }

    private static String b64(byte... bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private UserRegistrationFinalizationRequest validFinalizationRequest() {
        return new UserRegistrationFinalizationRequest(
                b64((byte) 1, (byte) 2, (byte) 3),    // kekSalt
                b64((byte) 10, (byte) 11),            // privateEd25519Crypto
                b64((byte) 20, (byte) 21),            // privateEd25519IV
                b64((byte) 30, (byte) 31),            // publicEd25519
                b64((byte) 40, (byte) 41),            // privateX25519Crypto
                b64((byte) 50, (byte) 51),            // privateX25519IV
                b64((byte) 60, (byte) 61)             // publicX25519
        );
    }

    private PasswordChangeRequest validPasswordChangeRequest() {
        return new PasswordChangeRequest(
                "newPassword",
                b64((byte) 1, (byte) 2, (byte) 3),    // kekSalt
                b64((byte) 10, (byte) 11),            // privateEd25519Crypto
                b64((byte) 20, (byte) 21),            // privateEd25519IV
                b64((byte) 40, (byte) 41),            // privateX25519Crypto
                b64((byte) 50, (byte) 51)             // privateX25519IV
        );
    }

    @Test
    void finalizeRegistration_shouldPersistCryptoSpecs() {
        UUID userId = UUID.randomUUID();
        UserRegistrationFinalizationRequest request = validFinalizationRequest();

        UserDB user = mock(UserDB.class);
        when(user.getId()).thenReturn(userId);

        when(userCryptoRepository.existsById(userId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userCryptoRepository.save(any(UserCryptoDB.class))).thenAnswer(inv -> inv.getArgument(0));

        UserCryptoDto result = authService.finalizeRegistration(userId, request);

        ArgumentCaptor<UserCryptoDB> captor = ArgumentCaptor.forClass(UserCryptoDB.class);
        verify(userCryptoRepository).save(captor.capture());

        UserCryptoDB saved = captor.getValue();
        assertThat(saved.getUserDB().getId()).isEqualTo(userId);
        assertThat(saved.getKekSalt()).containsExactly(1, 2, 3);
        assertThat(saved.getPrivateEd25519()).containsExactly(10, 11);
        assertThat(saved.getIvEd25519()).containsExactly(20, 21);
        assertThat(saved.getPublicEd25519()).containsExactly(30, 31);
        assertThat(saved.getPrivateX25519()).containsExactly(40, 41);
        assertThat(saved.getIvX25519()).containsExactly(50, 51);
        assertThat(saved.getPublicX25519()).containsExactly(60, 61);

        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.kekSalt()).isEqualTo(request.kekSalt());
        assertThat(result.publicEd25519()).isEqualTo(request.publicEd25519());

        verify(userSessionRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
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
    @SuppressWarnings("SuspiciousMethodCalls")
    void changePassword_shouldChangePassword() {
        UUID userId = UUID.randomUUID();
        UserDB user = mock(UserDB.class);
        UserCryptoDB userCryptoDB = mock(UserCryptoDB.class);

        List<UserSessionDB> userSessions = new ArrayList<>(List.of(mock(UserSessionDB.class), mock(UserSessionDB.class)));
        when(userSessionRepository.findAllByUser_Id(userId)).thenAnswer(_ -> new ArrayList<>(userSessions));

        doAnswer(invocation -> {
            userSessions.removeAll(invocation.getArgument(0));
            return null;
        }).when(userSessionRepository).deleteAll(ArgumentMatchers.<Iterable<UserSessionDB>>any());
        when(userSessionRepository.save(any(UserSessionDB.class))).thenAnswer(invocation -> {
            UserSessionDB session = invocation.getArgument(0);
            userSessions.add(session);
            return UserSessionDB.builder()
                    .id(UUID.randomUUID())
                    .user(session.getUser())
                    .accessToken(session.getAccessToken())
                    .accessTokenExpires(session.getAccessTokenExpires())
                    .refreshToken(session.getRefreshToken())
                    .refreshTokenExpires(session.getRefreshTokenExpires())
                    .build();
        });

        when(user.getId()).thenReturn(userId);
        when(user.getCrypto()).thenReturn(userCryptoDB);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserDB.class))).thenAnswer(invocation -> {
            UserDB userToPersist = invocation.getArgument(0);
            return UserDB.builder()
                    .id(UUID.randomUUID())
                    .username(userToPersist.getUsername())
                    .displayName(userToPersist.getDisplayName())
                    .passwordHash(userToPersist.getPasswordHash())
                    .registrationTimeUTC(userToPersist.getRegistrationTimeUTC())
                    .build();
        });

        when(passwordEncoder.encode("newPassword")).thenReturn("newPasswordHash");
        when(passwordEncoder.matches("newPassword", "newPasswordHash")).thenReturn(true);

        PasswordChangeRequest request = validPasswordChangeRequest();
        UserSessionDto result = authService.changePassword(userId, request);

        ArgumentCaptor<UserSessionDB> sessionCaptor = ArgumentCaptor.forClass(UserSessionDB.class);
        verify(userSessionRepository, times(1)).save(sessionCaptor.capture());

        UserSessionDB createdSession = sessionCaptor.getValue();

        assertThat(createdSession.getUser()).isSameAs(user);
        assertThat(createdSession.getAccessToken()).hasSize(32);
        assertThat(createdSession.getRefreshToken()).hasSize(32);
        assertThat(createdSession.getAccessTokenExpires()).isAfter(Instant.now());

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();

        assertThat(userSessionRepository.findAllByUser_Id(userId)).hasSize(1);
    }

    @Test
    void changePassword_shouldFailIfUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.changePassword(userId, validPasswordChangeRequest())).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void changePassword_shouldFailIfPasswordIdentical() {
        UUID userId = UUID.randomUUID();
        UserDB user = mock(UserDB.class);
        when(user.getId()).thenReturn(userId);
        when(user.getPasswordHash()).thenReturn("newPasswordHash");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(passwordEncoder.encode("newPassword")).thenReturn("newPasswordHash");
        when(passwordEncoder.matches("newPassword", "newPasswordHash")).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(userId, validPasswordChangeRequest())).isInstanceOf(PasswordConflictException.class);
    }

    @Test
    void refreshAccessToken_yieldsValidAccessToken() {
        byte[] refreshToken = new byte[32];
        secureRandom.nextBytes(refreshToken);

        UserSessionDB userSession = mock(UserSessionDB.class);
        when(userSessionRepository.findByRefreshToken(refreshToken)).thenReturn(Optional.of(userSession));
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

        UserSessionDto result = authService.refreshAccessToken(refreshToken);
        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.accessTokenExpires() > Instant.now().toEpochMilli()).isTrue();
        assertThat(result.refreshTokenExpires() > Instant.now().toEpochMilli()).isTrue();
    }
}
