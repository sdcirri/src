package it.sdc.src.service;

import it.sdc.src.db.entities.UserCryptoDB;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.entities.UserSessionDB;
import it.sdc.src.db.repositories.UserCryptoDBRepository;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.db.repositories.UserSessionDBRepository;
import it.sdc.src.dto.ContactCryptoDto;
import it.sdc.src.dto.UserCryptoDto;
import it.sdc.src.dto.UserDto;
import it.sdc.src.dto.UserSessionDto;
import it.sdc.src.dto.requests.UserRegistrationFinalizationRequest;
import it.sdc.src.dto.requests.UserRegistrationRequest;
import it.sdc.src.exceptions.LoginFailedException;
import it.sdc.src.exceptions.UserNotFoundException;
import it.sdc.src.exceptions.UsernameAlreadyTakenException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final PasswordEncoder PASSWORD_ENCODER = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final SecureRandom secureRandom;

    private final UserSessionDBRepository userSessionRepository;
    private final UserCryptoDBRepository userCryptoRepository;
    private final UserDBRepository userRepository;

    /**
     * Generate fresh access and refresh tokens
     * @return the new tokens
     */
    private byte[][] yieldTokens() {
        byte[] accessToken = new byte[32], refreshToken = new byte[32];
        secureRandom.nextBytes(accessToken);
        secureRandom.nextBytes(refreshToken);
        return new byte[][]{accessToken, refreshToken};
    }

    /**
     * Logs in the user yielding a new valid session
     * @param username username
     * @param password password
     * @return the new session, if credentials are valid
     * @throws LoginFailedException if credentials are invalid
     */
    public UserSessionDto login(String username, String password) {
        UserDB user = userRepository.findByUsernameAndPasswordHash(username, PASSWORD_ENCODER.encode(password))
                .orElseThrow(() -> new LoginFailedException("Invalid username or password"));

        byte[][] tokens = yieldTokens();
        UserSessionDB newSession = UserSessionDB.builder()
                .id(user.getId())
                .accessToken(tokens[0])
                .accessTokenExpires(Instant.now())
                .refreshToken(tokens[1])
                .refreshTokenExpires(Instant.now())
                .build();

        newSession = userSessionRepository.save(newSession);
        return toDto(newSession);
    }

    /**
     * Returns the crypto specs for the current user
     * @param userId current user ID
     * @return the user crypto specs
     * @throws LoginFailedException on bad user ID
     */
    public UserCryptoDto getMyCryptoSpecs(UUID userId) {
        UserCryptoDB userCrypto = userCryptoRepository.findById(userId).orElseThrow(
                () -> new LoginFailedException("User not found")
        );
        return toDto(userCrypto);
    }

    /**
     * Returns another user's public keys for contacting them
     * @param username user to contact
     * @return user's public keys
     */
    public ContactCryptoDto getUserCryptoSpecs(String username) {
        UserDB user = userRepository.findByUsername(username).orElseThrow(
                () -> new UserNotFoundException("User not found")
        );
        UserCryptoDB userCrypto = userCryptoRepository.findById(user.getId()).orElseThrow(
                () -> new UserNotFoundException("User not found")
        );
        Base64.Encoder encoder = Base64.getEncoder();
        return new ContactCryptoDto(
                encoder.encodeToString(userCrypto.getPublicEd25519()),
                encoder.encodeToString(userCrypto.getPublicX25519())
        );
    }

    /**
     * Refresh a user session
     * @param userId user ID
     * @param refreshToken current refresh token
     * @return a new user session
     * @throws LoginFailedException on bad refresh token
     */
    public UserSessionDto refreshAccessToken(UUID userId, String refreshToken) {
        UserSessionDB session = userSessionRepository.findByUserIdAndRefreshToken(
                userId,
                Base64.getDecoder().decode(refreshToken.getBytes())
        ).orElseThrow(
                () -> new LoginFailedException("Invalid refresh token")
        );
        byte[][] tokens = yieldTokens();

        UserSessionDB newSession = UserSessionDB.builder()
                .id(session.getId())
                .accessToken(tokens[0])
                .accessTokenExpires(Instant.now())
                .refreshToken(tokens[1])
                .refreshTokenExpires(Instant.now())
                .build();

        newSession = userSessionRepository.save(newSession);
        userSessionRepository.delete(session);

        return toDto(newSession);
    }

    /**
     * Registers a new user and logs them in
     * @param request registration request
     * @return a valid user session
     * @throws UsernameAlreadyTakenException if username is already taken
     */
    public UserSessionDto register(UserRegistrationRequest request) {
        if (userRepository.existsByUsername(request.username()))
            throw new UsernameAlreadyTakenException("Username is already taken");

        UserDB newUser = UserDB.builder()
                .username(request.username())
                .displayName(request.displayName())
                .registrationTimeUTC(Instant.now())
                .passwordHash(PASSWORD_ENCODER.encode(request.password()))
                .build();

        newUser = userRepository.save(newUser);

        byte[][] tokens = yieldTokens();
        UserSessionDB newSession = UserSessionDB.builder()
                .id(newUser.getId())
                .accessToken(tokens[0])
                .accessTokenExpires(Instant.now())
                .refreshToken(tokens[1])
                .refreshTokenExpires(Instant.now())
                .build();

        newSession = userSessionRepository.save(newSession);
        return toDto(newSession);
    }

    /**
     * Finalizes user registration with crypto specs
     * generated client-side
     * @param request registration finalization request with the required crypto specs
     * @return user crypto specs
     * @throws LoginFailedException on bad payload
     */
    public UserCryptoDto finalizeRegistration(UserRegistrationFinalizationRequest request) {
        UserDB newUser = userRepository.findById(request.id()).orElseThrow(
                () -> new LoginFailedException("Bad request")
        );

        Base64.Decoder decoder = Base64.getDecoder();
        UserCryptoDB newUserCrypto = UserCryptoDB.builder()
                .id(newUser.getId())
                .kekSalt(decoder.decode(request.kekSalt()))
                .privateEd25519(decoder.decode(request.privateX25519Crypto()))
                .ivEd25519(decoder.decode(request.privateEd25519IV()))
                .publicEd25519(decoder.decode(request.publicEd25519()))
                .privateX25519(decoder.decode(request.privateX25519Crypto()))
                .ivX25519(decoder.decode(request.privateX25519IV()))
                .publicX25519(decoder.decode(request.publicX25519()))
                .build();

        newUserCrypto = userCryptoRepository.save(newUserCrypto);
        return toDto(newUserCrypto);
    }

    /**
     * Change the user's display name
     * @param userId user ID
     * @param displayName the desired display name
     * @return the updated user info
     * @throws UserNotFoundException on bad user ID
     */
    public UserDto setDisplayName(UUID userId, String displayName) {
        UserDB user = userRepository.findById(userId).orElseThrow(
                () -> new UserNotFoundException("User not found")
        );
        user.setDisplayName(displayName);
        user = userRepository.save(user);
        return toDto(user);
    }

    /**
     * Change the user's unique username
     * @param userId user ID
     * @param username the desired username
     * @return the updated user info
     * @throws UserNotFoundException on bad user ID
     * @throws UsernameAlreadyTakenException when the desired username is already taken
     */
    public UserDto changeUsername(UUID userId, String username) {
        UserDB user = userRepository.findById(userId).orElseThrow(
                () -> new UserNotFoundException("User not found")
        );
        if (userRepository.existsByUsername(username))
            throw new UsernameAlreadyTakenException("Username is already taken");
        user.setUsername(username);
        user = userRepository.save(user);
        return toDto(user);
    }

    /**
     * Change the user's password
     * @param userId user ID
     * @param newPassword the new password to set
     * @throws UserNotFoundException on bad user ID
     */
    public void changePassword(UUID userId, String newPassword) {
        UserDB user = userRepository.findById(userId).orElseThrow(
                () -> new UserNotFoundException("User not found")
        );
        user.setPasswordHash(PASSWORD_ENCODER.encode(newPassword));
        userRepository.save(user);
    }

    private static UserDto toDto(UserDB user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getDisplayName()
        );
    }

    private static UserSessionDto toDto(UserSessionDB userSession) {
        return new UserSessionDto(
                userSession.getId(),
                Base64.getEncoder().encodeToString(userSession.getAccessToken()),
                userSession.getAccessTokenExpires().toEpochMilli(),
                Base64.getEncoder().encodeToString(userSession.getRefreshToken()),
                userSession.getRefreshTokenExpires().toEpochMilli()
        );
    }

    private static UserCryptoDto toDto(UserCryptoDB userCrypto) {
        Base64.Encoder encoder = Base64.getEncoder();
        return new UserCryptoDto(
                userCrypto.getId(),
                encoder.encodeToString(userCrypto.getKekSalt()),
                encoder.encodeToString(userCrypto.getPrivateEd25519()),
                encoder.encodeToString(userCrypto.getIvEd25519()),
                encoder.encodeToString(userCrypto.getPublicEd25519()),
                encoder.encodeToString(userCrypto.getPrivateX25519()),
                encoder.encodeToString(userCrypto.getIvX25519()),
                encoder.encodeToString(userCrypto.getPublicX25519())
        );
    }
}
