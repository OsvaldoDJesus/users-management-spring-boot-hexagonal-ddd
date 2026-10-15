package com.jcaa.usersmanagement.infrastructure.adapter.email;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BrevoConfig")
class BrevoConfigTest {

    @Test
    @DisplayName("acepta una API key REST y un remitente configurados")
    void shouldAcceptValidConfiguration() {
        assertDoesNotThrow(() -> config("xkeysib-valid", "sender@example.com"));
    }

    @Test
    @DisplayName("rechaza una API key vacia para evitar errores 401 tardios")
    void shouldRejectBlankApiKey() {
        assertThrows(IllegalArgumentException.class, () -> config("", "sender@example.com"));
    }

    @Test
    @DisplayName("rechaza credenciales SMTP usadas como API key")
    void shouldRejectSmtpKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> config("xsmtpsib-not-a-rest-api-key", "sender@example.com"));
    }

    @Test
    @DisplayName("rechaza un remitente vacio")
    void shouldRejectBlankSender() {
        assertThrows(IllegalArgumentException.class, () -> config("xkeysib-valid", ""));
    }

    private static BrevoConfig config(final String apiKey, final String fromAddress) {
        return new BrevoConfig(
                "https://api.brevo.com",
                apiKey,
                fromAddress,
                "Gestion de Usuarios",
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                3,
                Duration.ofMillis(500),
                2.0,
                50,
                10,
                5,
                Duration.ofSeconds(30));
    }
}
