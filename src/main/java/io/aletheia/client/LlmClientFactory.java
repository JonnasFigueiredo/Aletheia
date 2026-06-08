package io.aletheia.client;

import io.aletheia.config.AletheiaConfig;
import java.nio.file.Path;

/** Creates {@link LlmClient} instances from runtime configuration. */
public final class LlmClientFactory {

  private LlmClientFactory() {}

  /**
   * Returns a client matching {@code cfg.getProvider()}.
   *
   * @throws MissingCredentialsException if a real provider is selected but the API key is absent
   * @throws IllegalArgumentException if the provider name is unknown
   */
  public static LlmClient fromConfig(AletheiaConfig cfg) {
    return switch (cfg.getProvider().toLowerCase()) {
      case "mock" -> new MockLlmClient(Path.of(cfg.getFixturePath()));
      case "openai", "openai_compatible" -> {
        requireApiKey(cfg);
        yield new OpenAiCompatibleClient(cfg);
      }
      case "anthropic" -> {
        requireApiKey(cfg);
        yield new AnthropicClient(cfg);
      }
      default ->
          throw new IllegalArgumentException(
              "Unknown LLM provider: '"
                  + cfg.getProvider()
                  + "'. Valid values: mock, openai, openai_compatible, anthropic.");
    };
  }

  private static void requireApiKey(AletheiaConfig cfg) {
    if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
      throw new MissingCredentialsException(
          "LLM_API_KEY is required for provider '"
              + cfg.getProvider()
              + "' but is blank or not set.");
    }
  }
}
