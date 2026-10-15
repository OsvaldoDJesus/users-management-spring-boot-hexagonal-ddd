package com.jcaa.usersmanagement.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jcaa.usersmanagement.domain.enums.UserRole;
import com.jcaa.usersmanagement.domain.enums.UserStatus;
import com.jcaa.usersmanagement.domain.model.UserModel;
import com.jcaa.usersmanagement.domain.valueobject.UserEmail;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import com.jcaa.usersmanagement.domain.valueobject.UserName;
import com.jcaa.usersmanagement.domain.valueobject.UserPassword;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

  private static final String SECRET =
      Base64.getEncoder().encodeToString("a-test-secret-with-at-least-32-bytes".getBytes());
  private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");
  private static final long EXPIRATION_SECONDS = 900L;

  @Test
  void shouldGenerateAndParseSignedToken() {
    // Arrange
    final JwtTokenService service = serviceAt(NOW);
    final UserModel user = activeUser();

    // Act
    final String token = service.generate(user);
    final JwtPrincipal principal = service.parse(token);

    // Assert
    assertThat(principal.userId()).isEqualTo("user-001");
    assertThat(principal.role()).isEqualTo(UserRole.ADMIN);
    assertThat(service.expirationSeconds()).isEqualTo(EXPIRATION_SECONDS);
  }

  @Test
  void shouldRejectExpiredToken() {
    // Arrange
    final String token = serviceAt(NOW).generate(activeUser());
    final JwtTokenService laterService = serviceAt(NOW.plusSeconds(EXPIRATION_SECONDS + 1L));

    // Act
    final JwtException exception = assertThrows(ExpiredJwtException.class, () -> laterService.parse(token));

    // Assert
    assertThat(exception).isInstanceOf(ExpiredJwtException.class);
  }

  @Test
  void shouldRejectTokenSignedWithAnotherKey() {
    // Arrange
    final String token = serviceAt(NOW).generate(activeUser());
    final String differentSecret =
        Base64.getEncoder().encodeToString("another-test-secret-with-32-plus-bytes".getBytes());
    final JwtTokenService otherService =
        new JwtTokenService(
            new JwtProperties(differentSecret, EXPIRATION_SECONDS),
            Clock.fixed(NOW, ZoneOffset.UTC));

    // Act
    final JwtException exception = assertThrows(JwtException.class, () -> otherService.parse(token));

    // Assert
    assertThat(exception).isNotNull();
  }

  @Test
  void shouldAcceptUrlSafeBase64Secret() {
    // Arrange
    final String urlSafeSecret = urlSafeSecretWithDashAndUnderscore();
    final JwtTokenService service = serviceWithSecret(urlSafeSecret);

    // Act
    final JwtPrincipal principal = service.parse(service.generate(activeUser()));

    // Assert
    assertThat(urlSafeSecret).contains("-").contains("_");
    assertThat(principal.userId()).isEqualTo("user-001");
  }

  @Test
  void shouldTreatStandardAndUrlSafeEncodingsOfTheSameKeyAsEquivalent() {
    // Arrange
    final byte[] keyBytes = secretBytesWithUrlSafeCharacters();
    final JwtTokenService standardService =
        serviceWithSecret(Base64.getEncoder().encodeToString(keyBytes));
    final JwtTokenService urlSafeService = serviceWithSecret(urlSafeSecretWithDashAndUnderscore());

    // Act
    final JwtPrincipal principal = urlSafeService.parse(standardService.generate(activeUser()));

    // Assert
    assertThat(principal.userId()).isEqualTo("user-001");
  }

  private static JwtTokenService serviceWithSecret(final String secret) {
    return new JwtTokenService(
        new JwtProperties(secret, EXPIRATION_SECONDS), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static String urlSafeSecretWithDashAndUnderscore() {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytesWithUrlSafeCharacters());
  }

  // 0xFB 0xEF 0xBE encodes to "----" (URL-safe) and 0xFF to "_"; 33 bytes >= 256 bits for HS256.
  private static byte[] secretBytesWithUrlSafeCharacters() {
    final byte[] pattern = {(byte) 0xFB, (byte) 0xEF, (byte) 0xBE};
    final byte[] bytes = new byte[33];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = i < 15 ? pattern[i % pattern.length] : (byte) 0xFF;
    }
    return bytes;
  }

  private static JwtTokenService serviceAt(final Instant instant) {
    return new JwtTokenService(
        new JwtProperties(SECRET, EXPIRATION_SECONDS), Clock.fixed(instant, ZoneOffset.UTC));
  }

  private static UserModel activeUser() {
    return new UserModel(
        new UserId("user-001"),
        new UserName("Test User"),
        new UserEmail("test@example.com"),
        UserPassword.fromPlainText("SecurePass1"),
        UserRole.ADMIN,
        UserStatus.ACTIVE);
  }
}
