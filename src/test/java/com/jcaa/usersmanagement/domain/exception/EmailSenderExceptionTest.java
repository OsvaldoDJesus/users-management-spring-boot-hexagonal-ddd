package com.jcaa.usersmanagement.domain.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests para EmailSenderException.
 *
 * <p>De paso cubre el constructor de dos argumentos de {@code DomainException} (abstracta),
 * alcanzable sólo a través de {@code becauseSendFailed(cause)}.
 */
@DisplayName("EmailSenderException")
class EmailSenderExceptionTest {

  // ── becauseApiFailed()

  @Test
  @DisplayName("becauseApiFailed() incluye el email y el detalle de la API")
  void shouldFormatMessageWithEmailAndApiError() {
    // Arrange
    final String destinationEmail = "user@example.com";
    final String apiError = "HTTP 503";

    // Act
    final String message =
        EmailSenderException.becauseApiFailed(destinationEmail, apiError).getMessage();

    // Assert
    assertAll(
        "becauseApiFailed",
        () -> assertTrue(message.contains(destinationEmail), "el mensaje debe contener el email"),
        () -> assertTrue(message.contains(apiError), "el mensaje debe contener el error de la API"));
  }

  // ── becauseSendFailed()

  @Test
  @DisplayName("becauseSendFailed() debe encapsular la causa y producir un mensaje no vacío")
  void shouldWrapCauseAndProduceNonBlankMessage() {
    // Arrange
    final Throwable cause = new RuntimeException("IO error");

    // Act
    final EmailSenderException exception = EmailSenderException.becauseSendFailed(cause);

    // Assert
    assertAll(
        "becauseSendFailed",
        () -> assertSame(cause, exception.getCause(), "debe encapsular la causa original"),
        () ->
            assertFalse(
                exception.getMessage().isBlank(), "el mensaje por defecto no debe estar vacío"));
  }
}
