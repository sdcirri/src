package it.sdc.src.service.mapping;

import it.sdc.src.db.entities.UserCryptoDB;
import it.sdc.src.dto.ContactCryptoDto;
import it.sdc.src.dto.UserCryptoDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static it.sdc.src.test.fixtures.CryptoFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

public class UserCryptoMapperTest {
    private UserCryptoMapper userCryptoMapper;

    @BeforeEach
    void setUp() {
        userCryptoMapper = new UserCryptoMapper();
    }

    @Test
    void toPrivateDto_shouldReturnValidOwnCryptoSpecs() {
        UUID userId = UUID.randomUUID();
        UserCryptoDB userCrypto = mockUserCryptoDBSpecs(userId);
        UserCryptoDto result = userCryptoMapper.toPrivateDto(userCrypto);
        assertThat(result).isEqualTo(mockPrivateCryptoSpecs(userId));
    }

    @Test
    void toPublicDto_shouldReturnValidContactCryptoSpecs() {
        UserCryptoDB userCrypto = mockUserCryptoDBSpecs(UUID.randomUUID(), true);
        ContactCryptoDto result = userCryptoMapper.toPublicDto(userCrypto);
        assertThat(result).isEqualTo(mockPublicCryptoSpecs());
    }
}
