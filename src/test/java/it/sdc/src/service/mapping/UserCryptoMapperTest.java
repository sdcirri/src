package it.sdc.src.service.mapping;

import it.sdc.src.db.entities.UserCryptoDB;
import it.sdc.src.db.entities.UserDB;
import it.sdc.src.dto.ContactCryptoDto;
import it.sdc.src.dto.UserCryptoDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static it.sdc.src.test.fixtures.CryptoFixtures.*;
import static it.sdc.src.test.fixtures.UserFixtures.mockUserWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UserCryptoMapperTest {
    private UserCryptoMapper userCryptoMapper;

    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userCryptoMapper = new UserCryptoMapper();
        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(any())).thenReturn("hash");
    }

    @Test
    void toPrivateDto_shouldReturnValidOwnCryptoSpecs() {
        UserDB user = mockUserWithId(passwordEncoder);
        UserCryptoDB userCrypto = mockUserCryptoDBSpecs(user);
        UserCryptoDto result = userCryptoMapper.toPrivateDto(userCrypto);
        assertThat(result).isEqualTo(mockPrivateCryptoSpecs(user.getId()));
    }

    @Test
    void toPublicDto_shouldReturnValidContactCryptoSpecs() {
        UserCryptoDB userCrypto = mockUserCryptoDBSpecs(mockUserWithId(passwordEncoder));
        ContactCryptoDto result = userCryptoMapper.toPublicDto(userCrypto);
        assertThat(result).isEqualTo(mockPublicCryptoSpecs());
    }
}
