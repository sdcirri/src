package it.sdc.src.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class PasswordStrengthValidatorTest {
    private PasswordStrengthValidator validator;

    @BeforeEach
    void setUp() {
        this.validator = new PasswordStrengthValidator();
    }

    @Test
    void isValid_shouldAcceptNull() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void isValid_shouldAcceptStrongPassword() {
        String password = "Str0ng&UnPW3d!!!!";
        assertThat(validator.isValid(password, null)).isTrue();
    }


    @Test
    void isValid_shouldAcceptStrongPasswordAtMinLength() {
        String password = "Str0ng!!";
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @Test
    void isValid_shouldAcceptStrongPasswordAtMaxLength() {
        String password = "Aa1!".repeat(63) + "!!!";    // 4 * 63 + 3 = 252 + 3 = 255
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @Test
    void isValid_shouldRejectShortPassword() {
        String password = "Str0ng!";
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    void isValid_shouldRejectLongPassword() {
        String password = "Aa1!".repeat(64);            // 4 * 64 = 256
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "longbuttoosimple1!",       // no upper
            "LONGBUTTOOSIMPLE1!",       // no lower
            "LongButTooSimple1",        // no special
            "LongButTooSimple!"         // no number
    })
    void isValid_shouldRejectPasswordTooSimple(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }
}
