package com.jcaa.usersmanagement.infrastructure.adapter.email;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jcaa.usersmanagement.domain.exception.EmailSenderException;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("BrevoEmailSenderAdapter")
class BrevoEmailSenderAdapterTest {

    private static final String API_KEY = "xkeysib-test-api-key";
    private static final String DESTINATION = "john@example.com";

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        restClientBuilder =
                RestClient.builder()
                        .baseUrl("https://api.brevo.test")
                        .defaultHeader("api-key", API_KEY)
                        .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    @Test
    @DisplayName("envia el payload esperado e incluye una clave de idempotencia")
    void shouldSendExpectedPayload() {
        server
                .expect(once(), requestTo("https://api.brevo.test/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", API_KEY))
                .andExpect(jsonPath("$.sender.email").value("noreply@example.com"))
                .andExpect(jsonPath("$.to[0].email").value(DESTINATION))
                .andExpect(jsonPath("$.subject").value("Account created"))
                .andExpect(jsonPath("$.htmlContent").value("<html>Welcome</html>"))
                .andExpect(jsonPath("$.headers.idempotencyKey").isNotEmpty())
                .andRespond(withSuccess("{\"messageId\":\"message-1\"}", MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> adapter(defaultConfig(3, 5)).send(destination()));
        server.verify();
    }

    @Test
    @DisplayName("reintenta errores transitorios y finalmente envia")
    void shouldRetryTransientFailures() {
        server.expect(requestTo("https://api.brevo.test/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://api.brevo.test/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo("https://api.brevo.test/v3/smtp/email"))
                .andRespond(withSuccess());

        assertDoesNotThrow(() -> adapter(defaultConfig(3, 5)).send(destination()));
        server.verify();
    }

    @Test
    @DisplayName("no reintenta errores 4xx permanentes")
    void shouldNotRetryPermanentClientErrors() {
        server.expect(once(), requestTo("https://api.brevo.test/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        final EmailSenderException exception =
                assertThrows(
                        EmailSenderException.class,
                        () -> adapter(defaultConfig(3, 5)).send(destination()));

        assertTrue(exception.getMessage().contains("HTTP 400"));
        server.verify();
    }

    @Test
    @DisplayName("abre el circuito despues del umbral de fallos")
    void shouldOpenCircuitAfterFailureThreshold() {
        server.expect(requestTo("https://api.brevo.test/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://api.brevo.test/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        final BrevoEmailSenderAdapter adapter = adapter(defaultConfig(1, 2));

        assertThrows(EmailSenderException.class, () -> adapter.send(destination()));
        assertThrows(EmailSenderException.class, () -> adapter.send(destination()));
        final EmailSenderException openCircuit =
                assertThrows(EmailSenderException.class, () -> adapter.send(destination()));

        assertTrue(openCircuit.getMessage().contains("circuit breaker abierto"));
        server.verify();
    }

    private BrevoEmailSenderAdapter adapter(final BrevoConfig config) {
        return new BrevoEmailSenderAdapter(config, restClientBuilder.build());
    }

    private static BrevoConfig defaultConfig(final int attempts, final int minimumCalls) {
        return new BrevoConfig(
                "https://api.brevo.test",
                API_KEY,
                "noreply@example.com",
                "App Notifications",
                Duration.ofMillis(100),
                Duration.ofMillis(100),
                attempts,
                Duration.ofMillis(1),
                2.0,
                50,
                minimumCalls,
                minimumCalls,
                Duration.ofSeconds(30));
    }

    private static EmailDestinationModel destination() {
        return new EmailDestinationModel(
                DESTINATION, "John Doe", "Account created", "<html>Welcome</html>");
    }
}
