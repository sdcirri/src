package it.sdc.src.service;

import it.sdc.src.db.entities.UserDB;
import it.sdc.src.db.repositories.UserCryptoDBRepository;
import it.sdc.src.db.repositories.UserDBRepository;
import it.sdc.src.dto.UserCryptoDto;
import it.sdc.src.dto.UserSessionDto;
import it.sdc.src.dto.requests.UserRegistrationFinalizationRequest;
import it.sdc.src.dto.requests.UserRegistrationRequest;
import it.sdc.src.dto.requests.accountedits.PasswordChangeRequest;
import it.sdc.src.exceptions.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {
    private final PasswordEncoder passwordEncoder;

    private final UserCryptoDBRepository userCryptoRepository;
    private final UserDBRepository userRepository;

    private final SessionService sessionService;
    private final AccountService accountService;

    private final HibpClient hibpClient;

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

        if (!passwordEncoder.matches(password, user.getPasswordHash()))
            throw new LoginFailedException("Invalid password");

        return sessionService.yieldSession(user);
    }


    /**
     * Registers a new user and logs them in
     * @param request registration request
     * @return a valid user session
     * @throws UsernameAlreadyTakenException if username is already taken
     * @throws PwnedPasswordException if the password was involved in a data breach
     */
    public UserSessionDto register(UserRegistrationRequest request) {
        switch (hibpClient.isPwned(request.password())) {
            case PWNED -> throw new PwnedPasswordException("Password is involved in a data breach!");
            case SERVICE_UNAVAILABLE -> log.warn("Warning! HIBP unreachable, allowing user registration");
        }

        String hash = passwordEncoder.encode(request.password());
        return accountService.registerUser(request.username(), request.displayName(), hash);
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

        if (!passwordEncoder.matches(request.password(), newUser.getPasswordHash()))
            throw new BadPasswordException("Invalid password");

        Base64.Decoder decoder = Base64.getDecoder();
        return accountService.bootstrapUserCrypto(
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

    /**
     * Change the user's password, invalidating all previous sessions
     * @param userId user ID
     * @param request the new password to set and re-encrypted user secrets
     * @throws UserNotFoundException on bad user ID
     */
    public UserSessionDto changePassword(UUID userId, PasswordChangeRequest request) {
        UserDB user = userRepository.findById(userId).orElseThrow(
                () -> new UserNotFoundException("User not found")
        );
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash()))
            throw new BadPasswordException("Old password is not correct");
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash()))
            throw new PasswordConflictException("New password should not be the same as the old password");

        switch (hibpClient.isPwned(request.newPassword())) {
            case PWNED -> throw new PwnedPasswordException("Password is involved in a data breach!");
            case SERVICE_UNAVAILABLE -> log.warn("Warning! HIBP unreachable, allowing password change");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));

        Base64.Decoder decoder = Base64.getDecoder();

        accountService.changeUserPassword(
                userId,
                passwordEncoder.encode(request.newPassword()),
                decoder.decode(request.newKekSalt()),
                decoder.decode(request.newPrivateEd25519()),
                decoder.decode(request.newIvEd25519()),
                decoder.decode(request.newPrivateX25519()),
                decoder.decode(request.newIvX25519())
        );

        return sessionService.replaceSessions(user);
    }
}
