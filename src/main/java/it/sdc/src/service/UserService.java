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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserCryptoDBRepository userCryptoRepository;
    private final UserDBRepository userRepository;

    private final UserCryptoMapper userCryptoMapper;
    private final UserMapper userMapper;

    private final ProPicNormalizer proPicNormalizer;

    /**
     * Search for users by username
     * @param queryString prefix to match
     * @param currentPage current page index
     * @param myUserId current user ID, to exclude it from results
     * @return the list of matching users in batches of 10
     */
    public List<UserDto> searchUsers(String queryString, int currentPage, UUID myUserId) {
        return userRepository.searchByUsername(queryString.toLowerCase(), myUserId, PageRequest.of(currentPage, 10))
                .stream()
                .map(userMapper::toDto)
                .toList();
    }

    /**
     * Returns public user info
     * @param userId the user to look up
     * @return the user info
     */
    public UserDto getUserInfo(UUID userId) {
        UserDB user = userRepository.findById(userId).orElseThrow(
                () -> new UserNotFoundException("No such user ID: " + userId)
        );
        return userMapper.toDto(user);
    }

    /**
     * Returns the crypto specs for the current user
     * @param userId current user ID
     * @return the user crypto specs
     * @throws UserNotFoundException on bad user ID
     */
    public UserCryptoDto getMyCryptoSpecs(UUID userId) {
        UserCryptoDB userCrypto = userCryptoRepository.findById(userId).orElseThrow(
                () -> new UserNotFoundException("User not found")
        );
        return userCryptoMapper.toPrivateDto(userCrypto);
    }

    /**
     * Returns another user's public keys for contacting them
     * @param userId user to contact
     * @return user's public keys
     */
    public ContactCryptoDto getUserCryptoSpecs(UUID userId) {
        UserCryptoDB userCrypto = userCryptoRepository.findById(userId).orElseThrow(
                () -> new UserNotFoundException("User not found")
        );
        return userCryptoMapper.toPublicDto(userCrypto);
    }

    /**
     * Change the user's display name
     * @param userId user ID
     * @param displayName the desired display name
     * @return the updated user info
     * @throws UserNotFoundException on bad user ID
     */
    @Transactional
    public UserDto setDisplayName(UUID userId, String displayName) {
        UserDB user = userRepository.findById(userId).orElseThrow(
                () -> new UserNotFoundException("User not found")
        );
        user.setDisplayName(displayName);
        return userMapper.toDto(user);
    }

    /**
     * Change the user's unique username
     * @param userId user ID
     * @param username the desired username
     * @return the updated user info
     * @throws UserNotFoundException on bad user ID
     * @throws UsernameAlreadyTakenException when the desired username is already taken
     */
    @Transactional
    public UserDto changeUsername(UUID userId, String username) {
        UserDB user = userRepository.findById(userId).orElseThrow(
                () -> new UserNotFoundException("User not found")
        );
        // Guarantee idempotency without stressing the DB too much
        if (user.getUsername().equals(username))
            return userMapper.toDto(user);

        user.setUsername(username);

        try {
            userRepository.flush();
        }
        catch (DataIntegrityViolationException e) {
            throw new UsernameAlreadyTakenException("Username is already taken");
        }
        return userMapper.toDto(user);
    }

    /**
     * Change the user's profile picture
     * @param userId user ID
     * @param rawImage image uploaded by the user
     * @return the updated user info
     * @throws IOException when memory IO fails inside ProPicNormalizer.normalizeImage()
     */
    @Transactional
    public UserDto setProPic(UUID userId, byte[] rawImage) throws IOException {
        UserDB user = userRepository.findById(userId).orElseThrow(
                () -> new UserNotFoundException("User not found")
        );
        user.setProPic(proPicNormalizer.normalizeImage(rawImage));
        return userMapper.toDto(user);
    }
}
