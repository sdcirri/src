package it.sdc.src.service.mapping;

import it.sdc.src.db.entities.UserCryptoDB;
import it.sdc.src.dto.ContactCryptoDto;
import it.sdc.src.dto.UserCryptoDto;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class UserCryptoMapper {
    private static final Base64.Encoder encoder = Base64.getEncoder();

    public UserCryptoDto toPrivateDto(UserCryptoDB userCrypto) {
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

    public ContactCryptoDto toPublicDto(UserCryptoDB userCrypto) {
        return new ContactCryptoDto(
                encoder.encodeToString(userCrypto.getPublicEd25519()),
                encoder.encodeToString(userCrypto.getPublicX25519())
        );
    }
}
