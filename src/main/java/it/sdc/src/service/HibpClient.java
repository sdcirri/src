package it.sdc.src.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static it.sdc.src.utils.HashingUtils.sha1;

@Component
public class HibpClient {
    private static final String BASE_URL = "https://api.pwnedpasswords.com";
    private final RestClient client;

    public HibpClient(RestClient.Builder builder) {
        this.client = builder
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.USER_AGENT, "SRC/1.0")
                .defaultHeader("Add-Padding", "true")
                .build();
    }

    /**
     * Look up the user password on HIBP to check whether
     * it was involved in data breaches
     * @param password user chosen password
     * @return whether the password was involved in a data breach,
     *      HibpResult.SERVICE_UNAVAILABLE if the service was not reachable
     */
    public HibpResult isPwned(String password) {
        String sha1 = HexFormat.of().withUpperCase().formatHex(
                sha1(password.getBytes(StandardCharsets.UTF_8))
        );
        String prefix = sha1.substring(0, 5);
        String suffix = sha1.substring(5);

        try {
            String body = client.get()
                    .uri("/range/{prefix}", prefix)
                    .accept(MediaType.TEXT_PLAIN)
                    .retrieve()
                    .body(String.class);
            if (body == null)
                return HibpResult.SERVICE_UNAVAILABLE;

            boolean pwned = body.lines()
                    .map(line -> line.split(":", 2))
                    .filter(parts -> parts.length == 2)
                    .filter(parts -> !"0".equals(parts[1].trim()))
                    .map(parts -> parts[0].trim())
                    .anyMatch(suffix::equalsIgnoreCase);

            return pwned ? HibpResult.PWNED :  HibpResult.NOT_PWNED;

        } catch (RestClientException e) {
            return HibpResult.SERVICE_UNAVAILABLE;
        }
    }

    public enum HibpResult {
        PWNED,
        NOT_PWNED,
        SERVICE_UNAVAILABLE
    }
}
