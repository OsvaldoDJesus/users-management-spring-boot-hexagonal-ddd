package com.jcaa.usersmanagement.domain.exception;

public final class EmailSenderException extends DomainException {

  private static final String DEFAULT_MESSAGE = "La notificación por correo no pudo ser enviada.";
  private static final String API_MESSAGE_WITH_DETAIL =
      "No se pudo enviar el correo a '%s' mediante la API de Brevo: %s";

  public EmailSenderException(final String message) {
    super(message);
  }

  public EmailSenderException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public static EmailSenderException becauseSendFailed(final Throwable cause) {
    return new EmailSenderException(DEFAULT_MESSAGE, cause);
  }

  public static EmailSenderException becauseApiFailed(
      final String destinationEmail, final String detail) {
    return new EmailSenderException(String.format(API_MESSAGE_WITH_DETAIL, destinationEmail, detail));
  }
}
