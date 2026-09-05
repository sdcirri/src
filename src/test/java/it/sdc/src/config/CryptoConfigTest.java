package it.sdc.src.config;

import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

public class CryptoConfigTest {
    private final CryptoConfig config = new CryptoConfig();

    @Test
    void secureRandom_returnsInstance() {
        SecureRandom random = config.secureRandom();

        assertThat(random).isNotNull();
        assertThat(random).isNotSameAs(config.secureRandom());
    }

    @Test
    void sha512_returnsSha512Digest() {
        MessageDigest digest = config.sha512();

        assertThat(digest.getAlgorithm()).isEqualTo("SHA-512");
        assertThat(digest.digest(new byte[] {1, 2, 3})).hasSize(64);
    }

    @Test
    void sha512_throwsWhenAlgorithmUnavailable() {
        try (var messageDigest = mockStatic(MessageDigest.class)) {
            messageDigest.when(() -> MessageDigest.getInstance("SHA-512"))
                    .thenThrow(new NoSuchAlgorithmException("SHA-512"));

            assertThatThrownBy(config::sha512)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("SHA-512 hash algorithm is not available");
        }
    }
}
