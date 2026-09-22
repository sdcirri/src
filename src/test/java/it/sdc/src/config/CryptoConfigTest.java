package it.sdc.src.config;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class CryptoConfigTest {
    private final CryptoConfig config = new CryptoConfig();

    @Test
    void secureRandom_returnsInstance() {
        SecureRandom random = config.secureRandom();

        assertThat(random).isNotNull();
        assertThat(random).isNotSameAs(config.secureRandom());
    }
}
