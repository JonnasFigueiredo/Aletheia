package io.aletheia.client;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared retry configuration for real LLM provider clients.
 *
 * <p>Retries on transport failures ({@code statusCode == -1}), HTTP 429, and HTTP 5xx. Does not
 * retry on other 4xx codes. Uses exponential backoff: 500 ms, 1 s, 2 s (3 attempts total).
 */
public final class RetryPolicies {

  private static final Logger log = LoggerFactory.getLogger(RetryPolicies.class);

  static final Predicate<Throwable> RETRY_PREDICATE =
      e -> e instanceof LlmHttpException && ((LlmHttpException) e).isRetryable();

  private static final RetryConfig DEFAULT_CONFIG =
      RetryConfig.custom()
          .maxAttempts(3)
          .intervalFunction(attempt -> Math.min(500L * (1L << (attempt - 1)), 2000L))
          .retryOnException(RETRY_PREDICATE)
          .build();

  private static final RetryRegistry REGISTRY = RetryRegistry.of(DEFAULT_CONFIG);

  private RetryPolicies() {}

  /**
   * Returns a {@link Retry} with default exponential-backoff policy. Retries are logged at WARN via
   * SLF4J.
   */
  public static Retry defaultRetry(String name) {
    Retry retry = REGISTRY.retry(name);
    retry
        .getEventPublisher()
        .onRetry(
            event ->
                log.warn(
                    "Retry {}/{} for '{}': {}",
                    event.getNumberOfRetryAttempts(),
                    DEFAULT_CONFIG.getMaxAttempts(),
                    name,
                    event.getLastThrowable() != null
                        ? event.getLastThrowable().getMessage()
                        : "(no message)"));
    return retry;
  }

  /** Returns a zero-wait {@link Retry} suitable for unit tests. */
  static Retry noWaitRetry(String name) {
    RetryConfig cfg =
        RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(java.time.Duration.ZERO)
            .retryOnException(RETRY_PREDICATE)
            .build();
    Retry retry = Retry.of(name, cfg);
    retry
        .getEventPublisher()
        .onRetry(
            event ->
                log.debug(
                    "Test retry {}/3 for '{}': {}",
                    event.getNumberOfRetryAttempts(),
                    name,
                    event.getLastThrowable() != null
                        ? event.getLastThrowable().getMessage()
                        : "(no message)"));
    return retry;
  }
}
