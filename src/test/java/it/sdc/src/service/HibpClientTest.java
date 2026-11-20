package it.sdc.src.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import static it.sdc.src.service.HibpClient.HibpResult.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@RestClientTest(HibpClient.class)
class HibpClientTest {
    private static final String HIBP_URL = "https://api.pwnedpasswords.com/range/5BAA6";

    @Autowired
    private HibpClient hibpClient;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void isPwned_shouldReturnPwnedWhenHashIsPresent() {
        server.expect(requestTo(HIBP_URL))
                .andRespond(withSuccess("""
                        00000000000000000000000000000000000:1
                        1E4C9B93F3F0682250B6CF8331B7EE68FD8:123456
                        FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:2
                        """, MediaType.TEXT_PLAIN));

        assertThat(hibpClient.isPwned("password")).isEqualTo(PWNED);
    }

    @Test
    void isPwned_shouldReturnNotPwnedWhenHashIsNotPresent() {
        server.expect(requestTo(HIBP_URL))
                .andRespond(withSuccess("""
                        00000000000000000000000000000000000:1
                        FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:2
                        """, MediaType.TEXT_PLAIN));

        assertThat(hibpClient.isPwned("password")).isEqualTo(NOT_PWNED);
    }

    @Test
    void isPwned_shouldIgnorePaddingEntries() {
        server.expect(requestTo(HIBP_URL))
                .andRespond(withSuccess("""
                        1E4C9B93F3F0682250B6CF8331B7EE68FD8:0
                        FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:2
                        """, MediaType.TEXT_PLAIN));

        assertThat(hibpClient.isPwned("password")).isEqualTo(NOT_PWNED);
    }

    @Test
    void isPwned_shouldReturnServiceUnavailableOnServerError() {
        server.expect(requestTo(HIBP_URL))
                .andRespond(withStatus(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE
                ));

        assertThat(hibpClient.isPwned("password"))
                .isEqualTo(SERVICE_UNAVAILABLE);
    }

    @Test
    void isPwned_shouldReturnServiceUnavailableWhenBodyIsEmpty() {
        server.expect(requestTo(HIBP_URL)).andRespond(withSuccess());

        assertThat(hibpClient.isPwned("password")).isEqualTo(SERVICE_UNAVAILABLE);
    }

    @Test
    void isPwned_shouldSendOnlySha1PrefixAndRequiredHeaders() {
        server.expect(requestTo(HIBP_URL))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header(HttpHeaders.USER_AGENT, "SRC/1.0"))
                .andExpect(header("Add-Padding", "true"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN_VALUE))
                .andRespond(withSuccess(
                        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:1",
                        MediaType.TEXT_PLAIN
                ));

        hibpClient.isPwned("password");
    }

    @Test
    void isPwned_shouldIgnoreMalformedLines() {
        server.expect(requestTo(HIBP_URL))
                .andRespond(withSuccess("""
                        garbage
                        :123
                        FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:1
                        """, MediaType.TEXT_PLAIN));

        assertThat(hibpClient.isPwned("password")).isEqualTo(NOT_PWNED);
    }
}
