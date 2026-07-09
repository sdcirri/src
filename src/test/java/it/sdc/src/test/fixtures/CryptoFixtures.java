package it.sdc.src.test.fixtures;

import it.sdc.src.db.entities.UserCryptoDB;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.dto.ContactCryptoDto;
import it.sdc.src.dto.UserCryptoDto;
import it.sdc.src.dto.requests.UserRegistrationFinalizationRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.security.core.parameters.P;

import java.util.Base64;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        return mockUserCryptoDBSpecs(user, false);
    }

    public static UserCryptoDB mockUserCryptoDBSpecs(UserDB user, boolean publicOnly) {
        UserCryptoDB userCryptoDB = mock(UserCryptoDB.class);
        when(userCryptoDB.getPublicEd25519()).thenReturn(PUBLIC_ED25519);
        when(userCryptoDB.getPublicX25519()).thenReturn(PUBLIC_X25519);

        if (!publicOnly) {
            when(userCryptoDB.getUserDB()).thenReturn(user);
            when(userCryptoDB.getKekSalt()).thenReturn(KEK_SALT);
            when(userCryptoDB.getPrivateEd25519()).thenReturn(PRIVATE_ED25519_CRYPTO);
            when(userCryptoDB.getIvEd25519()).thenReturn(PRIVATE_ED25519_IV);
            when(userCryptoDB.getPrivateX25519()).thenReturn(PRIVATE_X25519_CRYPTO);
            when(userCryptoDB.getIvX25519()).thenReturn(PRIVATE_X25519_IV);
        }

        return userCryptoDB;
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
