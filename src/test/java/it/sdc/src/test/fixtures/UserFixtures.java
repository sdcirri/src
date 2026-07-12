package it.sdc.src.test.fixtures;

import it.sdc.src.db.entities.UserDB;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

public final class UserFixtures {
    public static final String USER_PASSWORD = "m#f$$#rdw89X&%jyD5b*mkf^";

    public static UserDB mockUser(PasswordEncoder passwordEncoder) {
        return mockUser(passwordEncoder, 1);
    }

    public static UserDB mockUser(PasswordEncoder passwordEncoder, int seq) {
        return UserDB.builder()
                .username("user"+seq)
                .displayName("User "+seq)
                .passwordHash(passwordEncoder.encode(USER_PASSWORD))
                .registrationTimeUTC(Instant.now())
                .build();
    }

    public static UserDB mockUserWithId(PasswordEncoder passwordEncoder) {
        return mockUserWithId(passwordEncoder, 1);
    }

    public static UserDB mockUserWithId(PasswordEncoder passwordEncoder, int seq) {
        return UserDB.builder()
                .id(UUID.randomUUID())
                .username("user"+seq)
                .displayName("User "+seq)
                .passwordHash(passwordEncoder.encode(USER_PASSWORD))
                .registrationTimeUTC(Instant.now())
                .build();
    }
}
