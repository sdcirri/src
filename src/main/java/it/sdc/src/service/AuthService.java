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
import it.sdc.src.exceptions.*;
import it.sdc.src.service.mapping.UserCryptoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final UserCryptoMapper userCryptoMapper;

    private final TokenIntrospectionCache tokenIntrospectionCache;

    private final AuthProperties authProperties;

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
     * Generate a new session for the user
     * @param user user
     * @return a new session for the user
     */
    private UserSessionDB yieldSession(UserDB user) {
        byte[][] tokens = yieldTokens();
        return userSessionRepository.save(UserSessionDB.builder()
                .user(user)
                .accessToken(tokens[0])
                .accessTokenExpires(Instant.now().plusSeconds(authProperties.getAccessTokenValiditySeconds()))
                .refreshToken(tokens[1])
                .refreshTokenExpires(Instant.now().plusSeconds(authProperties.getRefreshTokenValiditySeconds()))
                .build()
        );
    }

    /**
     * Logs in the user yielding a new valid session
     * @param username username
     * @param password password
     * @return the new session, if credentials are valid
     * @throws LoginFailedException if credentials are invalid
     */
    public UserSessionDto login(String username, String password) {
        UserDB user = userRepository.findByUsername(username)
                .orElseThrow(() -> new LoginFailedException("Invalid username"));

        if (!PASSWORD_ENCODER.matches(password, user.getPasswordHash()))
            throw new LoginFailedException("Invalid password");

        UserSessionDB newSession = yieldSession(user);
        return toDto(newSession);
    }

    /**
     * Refresh a user session
     * @param refreshToken current refresh token
     * @return a new user session
     * @throws LoginFailedException on bad refresh token
     */
    @Transactional
    public UserSessionDto refreshAccessToken(byte[] refreshToken) {
        UserSessionDB session = userSessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new LoginFailedException("Invalid refresh token"));

        tokenIntrospectionCache.evict(session);
        userSessionRepository.delete(session);
        UserSessionDB newSession = yieldSession(session.getUser());
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
        UserSessionDB newSession = yieldSession(newUser);
        return toDto(newSession);
    }

    /**
     * Finalizes user registration with crypto specs
     * generated client-side
     * @param userId partially registered user ID
     * @param request registration finalization request with the required crypto specs
     * @return user crypto specs
     * @throws LoginFailedException on bad payload
     */
    public UserCryptoDto finalizeRegistration(UUID userId, UserRegistrationFinalizationRequest request) {
        if (userCryptoRepository.existsById(userId))
            throw new CryptoConflictException("Attempt to re-init user crypto");

        UserDB newUser = userRepository.findById(userId).orElseThrow(
                () -> new LoginFailedException("Bad request")
        );

        Base64.Decoder decoder = Base64.getDecoder();
        UserCryptoDB newUserCrypto = UserCryptoDB.builder()
                .id(newUser.getId())
                .kekSalt(decoder.decode(request.kekSalt()))
                .privateEd25519(decoder.decode(request.privateEd25519Crypto()))
                .ivEd25519(decoder.decode(request.privateEd25519IV()))
                .publicEd25519(decoder.decode(request.publicEd25519()))
                .privateX25519(decoder.decode(request.privateX25519Crypto()))
                .ivX25519(decoder.decode(request.privateX25519IV()))
                .publicX25519(decoder.decode(request.publicX25519()))
                .build();

        newUserCrypto = userCryptoRepository.save(newUserCrypto);
        return userCryptoMapper.toDto(newUserCrypto);
    }

    /**
     * Change the user's password, invalidating all previous sessions
     * @param userId user ID
     * @param newPassword the new password to set
     * @throws UserNotFoundException on bad user ID
     */
    @Transactional
    public UserSessionDto changePassword(UUID userId, String newPassword) {
        UserDB user = userRepository.findById(userId).orElseThrow(
                () -> new UserNotFoundException("User not found")
        );
        if (PASSWORD_ENCODER.matches(newPassword, user.getPasswordHash()))
            throw new PasswordConflictException("New password should not be the same as the old password");
        user.setPasswordHash(PASSWORD_ENCODER.encode(newPassword));
        userRepository.save(user);

        tokenIntrospectionCache.evictAll(userSessionRepository.findAllByUser_Id(user.getId()));
        userSessionRepository.deleteAllByUser_Id(user.getId());
        UserSessionDB newSession = yieldSession(user);
        return toDto(newSession);
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
}
