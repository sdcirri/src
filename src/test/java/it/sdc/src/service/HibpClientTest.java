package it.sdc.src.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class HibpClientTest {
    private HttpClient httpClient;
    private HibpClient hibpClient;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        hibpClient = new HibpClient(httpClient);
    }

    @AfterEach
    void clearInterruptedFlag() {
        Thread.interrupted();
    }

    @Test
    void isPwned_shouldReturnTrue_whenHashIsPresent() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);

        // SHA-1("password"):
        // 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                00000000000000000000000000000000000:1
                1E4C9B93F3F0682250B6CF8331B7EE68FD8:123456
                FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:2
                """);

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);

        assertThat(hibpClient.isPwned("password")).isTrue();
    }

    @Test
    void isPwned_shouldReturnFalse_whenHashIsNotPresent() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                00000000000000000000000000000000000:1
                FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:2
                """);

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);

        assertThat(hibpClient.isPwned("password")).isFalse();
    }

    @Test
    void isPwned_shouldReturnFalse_whenServiceIsUnavailable() throws Exception {
        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenThrow(new IOException("HIBP unavailable"));

        assertThat(hibpClient.isPwned("password")).isFalse();
    }

    @Test
    void isPwned_shouldReturnFalse_whenServiceReturnsNon200() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(503);

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);

        assertThat(hibpClient.isPwned("password")).isFalse();

        verify(response, never()).body();
    }

    @Test
    void isPwned_shouldRestoreInterruptAndThrow_whenRequestIsInterrupted() throws Exception {
        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenThrow(new InterruptedException("interrupted"));

        assertThatThrownBy(() -> hibpClient.isPwned("password"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("HIBP request interrupted")
                .hasCauseInstanceOf(InterruptedException.class);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void isPwned_shouldQueryHibpUsingOnlySha1Prefix() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("");

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);

        hibpClient.isPwned("password");

        var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        verify(httpClient).send(
                requestCaptor.capture(),
                any(HttpResponse.BodyHandler.class)
        );

        assertThat(requestCaptor.getValue().uri().toString())
                .isEqualTo("https://api.pwnedpasswords.com/range/5BAA6");
    }
}
