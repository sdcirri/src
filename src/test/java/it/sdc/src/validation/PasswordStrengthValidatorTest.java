package it.sdc.src.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class PasswordStrengthValidatorTest {
    private PasswordStrengthValidator validator;
    private HttpClient pwnClient;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        pwnClient = mock(HttpClient.class);
        stubHibpResponse(pwnClient, "");
        validator = new PasswordStrengthValidator(pwnClient);
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

    @Test
    void isValid_shouldRejectPwnedPassword() throws IOException, InterruptedException {
        String password = "Str0ngButPwn3d!!!!", sha1sum = sha1(password);
        String suffix = sha1sum.substring(5);
        stubHibpResponse(pwnClient, suffix + ":69");
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    void isValid_shouldAcceptWhenHIBPUnreachable() throws IOException, InterruptedException {
        when(pwnClient.send(any(), any())).thenThrow(new IOException("HIBP Down!"));
        assertThat(validator.isValid("Str0ng&UnPW3d!!!!", null)).isTrue();
    }

    @Test
    void isValid_throwsWhenSha1Unavailable() {
        try (var messageDigest = mockStatic(MessageDigest.class)) {
            messageDigest.when(() -> MessageDigest.getInstance("SHA-1"))
                    .thenThrow(new NoSuchAlgorithmException("SHA-1"));

            assertThatThrownBy(() -> validator.isValid("Str0ng&UnPW3d!!!!", null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("SHA-1 algorithm not supported");
        }
    }

    @SuppressWarnings("unchecked")
    private static void stubHibpResponse(HttpClient client, String body) throws IOException, InterruptedException {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.body()).thenReturn(body);
        doReturn(response).when(client).send(any(), any());
    }

    private static String sha1(String password) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(hash);
        } catch (NoSuchAlgorithmException ignored) {
            // Impossible on compliant JDK
            return "";
        }
    }
}
