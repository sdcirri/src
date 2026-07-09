package it.sdc.src.test.fixtures;

import it.sdc.src.db.entities.UserCryptoDB;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.dto.ContactCryptoDto;
import it.sdc.src.dto.UserCryptoDto;
import it.sdc.src.dto.requests.UserRegistrationFinalizationRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Base64;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CryptoFixtures {
    private static final byte[] KEK_SALT = new byte[] {1};
    private static final byte[] PRIVATE_ED25519_CRYPTO = new byte[] {2, 3, 4};
    private static final byte[] PRIVATE_ED25519_IV = new byte[] {5, 6};
    private static final byte[] PUBLIC_ED25519 = new byte[] {7, 8};
    private static final byte[] PRIVATE_X25519_CRYPTO = new byte[] {9, 10, 11};
    private static final byte[] PRIVATE_X25519_IV = new byte[] {12, 13};
    private static final byte[] PUBLIC_X25519 = new byte[] {14, 15};

    private static final Base64.Encoder ENCODER = Base64.getEncoder();

    public static UserCryptoDB mockUserCryptoDBSpecs(UserDB user) {
        return UserCryptoDB.builder()
                .userDB(user)
                .kekSalt(KEK_SALT)
                .privateEd25519(PRIVATE_ED25519_CRYPTO)
                .ivEd25519(PRIVATE_ED25519_IV)
                .publicEd25519(PUBLIC_ED25519)
                .privateX25519(PRIVATE_X25519_CRYPTO)
                .ivX25519(PRIVATE_X25519_IV)
                .publicX25519(PUBLIC_X25519)
                .build();
    }

    public static UserCryptoDto mockPrivateCryptoSpecs(UUID userId) {
        return new UserCryptoDto(
                userId,
                ENCODER.encodeToString(KEK_SALT),
                ENCODER.encodeToString(PRIVATE_ED25519_CRYPTO),
                ENCODER.encodeToString(PRIVATE_ED25519_IV),
                ENCODER.encodeToString(PUBLIC_ED25519),
                ENCODER.encodeToString(PRIVATE_X25519_CRYPTO),
                ENCODER.encodeToString(PRIVATE_X25519_IV),
                ENCODER.encodeToString(PUBLIC_X25519)
        );
    }

    public static ContactCryptoDto mockPublicCryptoSpecs() {
        return new ContactCryptoDto(
                ENCODER.encodeToString(PUBLIC_ED25519),
                ENCODER.encodeToString(PUBLIC_X25519)
        );
    }

    public static UserRegistrationFinalizationRequest mockFinalizationRequest() {
        return new UserRegistrationFinalizationRequest(
                ENCODER.encodeToString(KEK_SALT),
                ENCODER.encodeToString(PRIVATE_ED25519_CRYPTO),
                ENCODER.encodeToString(PRIVATE_ED25519_IV),
                ENCODER.encodeToString(PUBLIC_ED25519),
                ENCODER.encodeToString(PRIVATE_X25519_CRYPTO),
                ENCODER.encodeToString(PRIVATE_X25519_IV),
                ENCODER.encodeToString(PUBLIC_X25519)
        );
    }
}
