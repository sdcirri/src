package it.sdc.src.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

public class Base64StringValidatorTest {
    private static final Base64.Encoder ENCODER = Base64.getEncoder();

    private final Base64StringValidator validator = new Base64StringValidator();

    static String[] GOOD_STRINGS = new String[] {
            "AA",
            "",
            // not Base64, but validator should ignore
            null,
            ENCODER.encodeToString("Just a normal string".getBytes()),
            // mock AES-GCM vector specifically
            ENCODER.encodeToString(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}),
            // message data can go up to 20MB, better stress test
            "A".repeat(20 * 1024 * 1024)
    };

    static String[] BAD_STRINGS = new String[] {
            "A",        // java.lang.IllegalArgumentException: Input byte[] should at least have 2 bytes for base64 bytes
            "AA====",
            "definitely not Base64",
            "====",
            "\0".repeat(20)
    };

    @ParameterizedTest
    @FieldSource("GOOD_STRINGS")
    void isValid_shouldAcceptGoodStrings(String goodString) {
        assertThat(validator.isValid(goodString, null)).isTrue();
    }

    @ParameterizedTest
    @FieldSource("BAD_STRINGS")
    void isValid_shouldNotAcceptBadStrings(String badString) {
        assertThat(validator.isValid(badString, null)).isFalse();
    }
}
