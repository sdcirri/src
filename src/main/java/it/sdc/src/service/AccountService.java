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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final UserCryptoDBRepository userCryptoRepository;
    private final UserDBRepository userRepository;

    private final UserCryptoMapper userCryptoMapper;

    private final SessionService sessionService;

    /**
     * Registers a new user and yields a session for that user
     * @param username username to set
     * @param displayName display name to set (optional)
     * @param passwordHash password hash, from PasswordEncoder
     * @return the user session
     * @throws UsernameAlreadyTakenException if the username already exists on DB
     */
    @Transactional
    public UserSessionDto registerUser(String username, String displayName, String passwordHash) {
        if (userRepository.existsByUsername(username))
            throw new UsernameAlreadyTakenException("Username is already taken");

        UserDB newUser = UserDB.builder()
                .username(username)
                .displayName(displayName)
                .registrationTimeUTC(Instant.now())
                .passwordHash(passwordHash)
                .build();

        newUser = userRepository.save(newUser);
        return sessionService.yieldSession(newUser);
    }

    /**
     * Bootstraps a new user's keychain, all secrets are client-generated
     * and client-encrypted
     * @param userId user ID
     * @param kekSalt KEK salt
     * @param privateEd25519 private Ed25519 key (encrypted)
     * @param ivEd25519 IV for Ed25519 key decryption
     * @param publicEd25519 public Ed25519 key
     * @param privateX25519 private X25519 key (encrypted)
     * @param ivX25519 IV for X25519 key decryption
     * @param publicX25519 public X25519 key
     */
    @Transactional
    public UserCryptoDto bootstrapUserCrypto(
            UUID userId,
            byte[] kekSalt,
            byte[] privateEd25519,
            byte[] ivEd25519,
            byte[] publicEd25519,
            byte[] privateX25519,
            byte[] ivX25519,
            byte[] publicX25519
    ) {
        UserDB user = userRepository.findById(userId).orElseThrow(
                () -> new UserNotFoundException("User not found")
        );

        UserCryptoDB newUserCrypto = UserCryptoDB.builder()
                .userDB(user)
                .kekSalt(kekSalt)
                .privateEd25519(privateEd25519)
                .ivEd25519(ivEd25519)
                .publicEd25519(publicEd25519)
                .privateX25519(privateX25519)
                .ivX25519(ivX25519)
                .publicX25519(publicX25519)
                .build();

        newUserCrypto = userCryptoRepository.save(newUserCrypto);
        return userCryptoMapper.toPrivateDto(newUserCrypto);
    }

    /**
     * Change a user's password. A password change implies re-encrypting
     * client-side the crypto keychain, so the new specs must also be passed
     * @param userId user ID
     * @param passwordHash password hash to set, from PasswordEncoder
     * @param kekSalt new KEK salt
     * @param privateEd25519 new private Ed25519 key (encrypted)
     * @param ivEd25519 new private Ed25519 key IV
     * @param privateX25519 new private X25519 key (encrypted)
     * @param ivX25519 new private X25519 key IV
     */
    @Transactional
    public void changeUserPassword(
            UUID userId,
            String passwordHash,
            byte[] kekSalt,
            byte[] privateEd25519,
            byte[] ivEd25519,
            byte[] privateX25519,
            byte[] ivX25519
    ) {
        UserDB user = userRepository.findById(userId).orElseThrow(
                () -> new UserNotFoundException("User not found")
        );
        user.setPasswordHash(passwordHash);

        UserCryptoDB crypto = user.getCrypto();

        crypto.setKekSalt(kekSalt);
        crypto.setIvEd25519(ivEd25519);
        crypto.setPrivateEd25519(privateEd25519);
        crypto.setIvX25519(ivX25519);
        crypto.setPrivateX25519(privateX25519);

        userRepository.save(user);
    }
}
