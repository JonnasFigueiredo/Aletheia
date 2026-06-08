package io.aletheia.client;

import io.aletheia.model.LlmRequest;
import io.aletheia.model.LlmResponse;
import java.util.Optional;

/** Abstraction over LLM provider backends. */
public interface LlmClient {

  /**
   * Sends a completion request to the provider and returns the response.
   *
   * @throws LlmHttpException if the provider returns an error or the network fails
   */
  LlmResponse complete(LlmRequest request);

  /**
   * Returns a provider-native embedding vector for {@code text}, or empty when unsupported.
   *
   * <p>Callers should fall back to an offline similarity metric when this returns empty.
   */
  default Optional<double[]> embed(String text) {
    return Optional.empty();
  }

  /**
   * Stable identifier for this provider, e.g. {@code "mock"}, {@code "openai"}, {@code
   * "anthropic"}.
   */
  String providerId();
}
