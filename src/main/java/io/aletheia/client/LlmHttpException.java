package io.aletheia.client;

/**
 * Thrown when an LLM provider returns an error HTTP status or when the network call fails.
 *
 * <p>Use {@code statusCode == -1} to indicate a transport-level failure (IOException, timeout).
 * Codes 429 and &ge;500 are retriable by {@link RetryPolicies}.
 */
public final class LlmHttpException extends RuntimeException {

  private final int statusCode;

  /**
   * @param statusCode HTTP status code, or {@code -1} for transport failures
   * @param message human-readable description
   */
  public LlmHttpException(int statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
  }

  /** HTTP status code, or {@code -1} for transport-level errors. */
  public int getStatusCode() {
    return statusCode;
  }

  /** Returns true when this error should be retried (429, 5xx, or transport failure). */
  public boolean isRetryable() {
    return statusCode == -1 || statusCode == 429 || statusCode >= 500;
  }
}
