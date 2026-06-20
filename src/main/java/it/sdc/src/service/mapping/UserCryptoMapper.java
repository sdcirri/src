package it.sdc.src.service.mapping;

import it.sdc.src.db.entities.UserCryptoDB;
import it.sdc.src.dto.UserCryptoDto;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class UserCryptoMapper {
    public UserCryptoDto toDto(UserCryptoDB userCrypto) {
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
