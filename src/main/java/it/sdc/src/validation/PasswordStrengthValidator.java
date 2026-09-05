package it.sdc.src.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

public class PasswordStrengthValidator implements ConstraintValidator<StrongPassword, String> {
    private static final Pattern[] MANDATORY_PATTERNS = {
            Pattern.compile("[A-Z]"),
            Pattern.compile("[a-z]"),
            Pattern.compile("[0-9]"),
            Pattern.compile("[^A-Za-z0-9]")
    };

    private final HttpClient httpClient;

    public PasswordStrengthValidator() {
        this(HttpClient.newHttpClient());
    }

    // package-private constructor for tests
    PasswordStrengthValidator(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

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

    /**
     * Look up the user password on HIBP to check whether
     * it was involved in data breaches
     * If the service is unavailable the password is considered not PWNed
     * @param password user chosen password
     * @return whether the password was involved in a data breach
     */
    private boolean isPwned(String password) {
        String sha1;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(password.getBytes(StandardCharsets.UTF_8));
            sha1 = HexFormat.of().withUpperCase().formatHex(hash);
        }
        // Impossible on compliant JDK
        catch (NoSuchAlgorithmException ignored) {
            throw new IllegalStateException("SHA-1 algorithm not supported");
        }

        String prefix = sha1.substring(0, 5), response;
        try {
            response = httpClient.send(
                    HttpRequest.newBuilder(URI.create("https://api.pwnedpasswords.com/range/" + prefix)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            ).body();
        }
        catch (IOException | InterruptedException e) {
            // Service unavailable
            return false;
        }

        for (String line : response.split("\n")) {
            String pwnedHash = prefix + line.split(":")[0].toUpperCase();
            if (sha1.equals(pwnedHash))
                return true;
        }
        return false;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        return value == null || (isStrong(value) && !isPwned(value));
    }
}
