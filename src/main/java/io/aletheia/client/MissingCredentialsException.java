package io.aletheia.client;

/** Thrown when a real provider is selected but {@code LLM_API_KEY} is absent or blank. */
public final class MissingCredentialsException extends RuntimeException {

  public MissingCredentialsException(String message) {
    super(message);
  }
}
