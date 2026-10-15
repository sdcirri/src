package it.sdc.src.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

import static it.sdc.src.utils.HashingUtils.sha1;

@Component
// Package constructor for testability
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class HibpClient {
    private final HttpClient client;

    public HibpClient() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Look up the user password on HIBP to check whether
     * it was involved in data breaches
     * If the service is unavailable the password is considered not PWNed
     * @param password user chosen password
     * @return whether the password was involved in a data breach
     */
    public boolean isPwned(String password) {
        String sha1 = HexFormat.of().withUpperCase().formatHex(
                sha1(password.getBytes(StandardCharsets.UTF_8))
        );
        String prefix = sha1.substring(0, 5);

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.pwnedpasswords.com/range/" + prefix))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> response;

        try {
            response = client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            // Service unavailable
            return false;
        } catch (InterruptedException e) {
            // Interrupted
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HIBP request interrupted", e);
        }

        if (response.statusCode() != 200)
            return false;

        for (String line : response.body().split("\n")) {
            String pwnedHash = prefix + line.split(":")[0].toUpperCase();
            if (sha1.equals(pwnedHash))
                return true;
        }
        return false;
    }
}
