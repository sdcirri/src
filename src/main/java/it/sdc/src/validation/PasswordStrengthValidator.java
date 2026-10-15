package it.sdc.src.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.regex.Pattern;

import static it.sdc.src.utils.HashingUtils.sha1;

public class PasswordStrengthValidator implements ConstraintValidator<StrongPassword, String> {
    private static final Pattern[] MANDATORY_PATTERNS = {
            Pattern.compile("[A-Z]"),
            Pattern.compile("[a-z]"),
            Pattern.compile("[0-9]"),
            Pattern.compile("[^A-Za-z0-9]")
    };

    /**
     * Check the password for:
     *  - at least one uppercase letter
     *  - at least one lowercase letter
     *  - at least one digit
     *  - at least one special char
     *  - length between 8 and 255
     * If any of those conditions is not met, the password is considered weak
     * @param password user chosen password
     * @return whether the password is strong or not
     */
    private static boolean isStrong(String password) {
        // Also prevent too long passwords in order not to DoS Argon2
        if (password.length() < 8 || password.length() > 255)
            return false;

        for (Pattern pattern : MANDATORY_PATTERNS) {
            if (!pattern.matcher(password).find())
                return false;
        }
        return true;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        return value == null || isStrong(value);
    }
}
