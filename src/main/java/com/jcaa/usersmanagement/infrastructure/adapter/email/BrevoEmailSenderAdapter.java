package com.jcaa.usersmanagement.infrastructure.adapter.email;

import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
import com.jcaa.usersmanagement.domain.exception.EmailSenderException;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class BrevoEmailSenderAdapter implements EmailSenderPort {

  private static final String SEND_EMAIL_PATH = "/v3/smtp/email";
  private static final String IDEMPOTENCY_HEADER = "idempotencyKey";

  private final BrevoConfig config;
  private final RestClient restClient;
  private final Retry retry;
  private final CircuitBreaker circuitBreaker;

  public BrevoEmailSenderAdapter(final BrevoConfig config, final RestClient brevoRestClient) {
    this.config = config;
    this.restClient = brevoRestClient;
    this.retry = buildRetry(config);
    this.circuitBreaker = buildCircuitBreaker(config);
    this.retry
        .getEventPublisher()
        .onRetry(
            event ->
                log.warn(
                    "[BrevoEmailSenderAdapter] reintento {} tras fallo transitorio: {}",
                    event.getNumberOfRetryAttempts(),
                    exceptionType(event.getLastThrowable())));
  }

  @Override
  public void send(final EmailDestinationModel destination) {
    final BrevoEmailRequest request = buildRequest(destination);
    final Runnable protectedRequest =
        Retry.decorateRunnable(
            retry, CircuitBreaker.decorateRunnable(circuitBreaker, () -> sendRequest(request)));
    try {
      protectedRequest.run();
      log.info(
          "[BrevoEmailSenderAdapter] correo aceptado por Brevo para {}.",
          destination.getDestinationEmail());
    } catch (final RestClientResponseException exception) {
      throw EmailSenderException.becauseApiFailed(
          destination.getDestinationEmail(), "HTTP " + exception.getStatusCode().value());
    } catch (final CallNotPermittedException exception) {
      throw EmailSenderException.becauseApiFailed(
          destination.getDestinationEmail(), "circuit breaker abierto");
    } catch (final RestClientException exception) {
      throw EmailSenderException.becauseApiFailed(
          destination.getDestinationEmail(), exception.getClass().getSimpleName());
    }
  }

  private void sendRequest(final BrevoEmailRequest request) {
    restClient.post().uri(SEND_EMAIL_PATH).body(request).retrieve().toBodilessEntity();
  }

  private BrevoEmailRequest buildRequest(final EmailDestinationModel destination) {
    return new BrevoEmailRequest(
        new BrevoEmailRequest.Contact(config.fromAddress(), config.fromName()),
        List.of(
            new BrevoEmailRequest.Contact(
                destination.getDestinationEmail(), destination.getDestinationName())),
        destination.getSubject(),
        destination.getBody(),
        Map.of(IDEMPOTENCY_HEADER, UUID.randomUUID().toString()));
  }

  private static Retry buildRetry(final BrevoConfig config) {
    final IntervalFunction backoff =
        IntervalFunction.ofExponentialBackoff(
            config.retryInitialDelay().toMillis(), config.retryMultiplier());
    final RetryConfig retryConfig =
        RetryConfig.custom()
            .maxAttempts(config.retryMaxAttempts())
            .intervalFunction(backoff)
            .retryOnException(BrevoEmailSenderAdapter::isTransientFailure)
            .build();
    return Retry.of("brevo-email", retryConfig);
  }

  private static CircuitBreaker buildCircuitBreaker(final BrevoConfig config) {
    final Predicate<Throwable> transientFailure = BrevoEmailSenderAdapter::isTransientFailure;
    final CircuitBreakerConfig circuitConfig =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(config.circuitFailureThreshold())
            .slidingWindowSize(config.circuitSlidingWindowSize())
            .minimumNumberOfCalls(config.circuitMinimumCalls())
            .waitDurationInOpenState(config.circuitOpenDuration())
            .recordException(transientFailure)
            .build();
    return CircuitBreaker.of("brevo-email", circuitConfig);
  }

  private static boolean isTransientFailure(final Throwable exception) {
    if (exception instanceof ResourceAccessException
        || exception instanceof HttpServerErrorException) {
      return true;
    }
    if (exception instanceof HttpClientErrorException clientError) {
      final HttpStatusCode status = clientError.getStatusCode();
      return status.value() == 408 || status.value() == 429;
    }
    return false;
  }

  private static String exceptionType(final Throwable exception) {
    return Objects.isNull(exception) ? "causa desconocida" : exception.getClass().getSimpleName();
  }
}
