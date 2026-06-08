package io.aletheia.eval;

import io.aletheia.client.LlmClient;
import io.aletheia.model.LlmRequest;
import io.aletheia.model.LlmResponse;
import java.util.Optional;

/** Lightweight test double for {@link LlmClient}. Not thread-safe. */
final class FakeLlmClient implements LlmClient {

  private final String responseText;
  private final double[] embedding;
  private final String provider;

  /** Client that returns {@code responseText} from {@code complete()} with no embedding support. */
  FakeLlmClient(String responseText) {
    this(responseText, null, "fake");
  }

  /** Client that supports embeddings (both {@code complete()} and {@code embed()} respond). */
  FakeLlmClient(String responseText, double[] embedding) {
    this(responseText, embedding, "fake");
  }

  private FakeLlmClient(String responseText, double[] embedding, String provider) {
    this.responseText = responseText;
    this.embedding = embedding;
    this.provider = provider;
  }

  /** Returns a client that self-identifies as {@code "mock"} so judge evaluators use heuristics. */
  static FakeLlmClient asMock(String responseText) {
    return new FakeLlmClient(responseText, null, "mock");
  }

  @Override
  public LlmResponse complete(LlmRequest request) {
    return new LlmResponse(responseText, 10, 20, 100);
  }

  @Override
  public Optional<double[]> embed(String text) {
    return embedding != null ? Optional.of(embedding) : Optional.empty();
  }

  @Override
  public String providerId() {
    return provider;
  }
}
