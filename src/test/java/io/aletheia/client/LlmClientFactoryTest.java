package io.aletheia.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aletheia.config.AletheiaConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link LlmClientFactory}. */
class LlmClientFactoryTest {

  private static final String MINIMAL_FIXTURE =
      "- id: t\n"
          + "  match: x\n"
          + "  response: y\n"
          + "  tokens_in: 1\n"
          + "  tokens_out: 1\n"
          + "  latency_ms: 1\n";

  @Test
  void fromConfig_mock_returnsMockClient(@TempDir Path tmp) throws IOException {
    Path fixture = tmp.resolve("mock.yaml");
    Files.writeString(fixture, MINIMAL_FIXTURE);
    AletheiaConfig cfg =
        AletheiaConfig.of(
            "mock", null, null, null, 0.0, "text-embedding-3-small", fixture.toString(), 30);

    LlmClient client = LlmClientFactory.fromConfig(cfg);

    assertThat(client).isInstanceOf(MockLlmClient.class);
    assertThat(client.providerId()).isEqualTo("mock");
  }

  @Test
  void fromConfig_openai_returnsOpenAiClient() {
    AletheiaConfig cfg = AletheiaConfig.of("openai", null, "sk-test", "gpt-4o-mini", 0.0);
    LlmClient client = LlmClientFactory.fromConfig(cfg);
    assertThat(client).isInstanceOf(OpenAiCompatibleClient.class);
    assertThat(client.providerId()).isEqualTo("openai");
  }

  @Test
  void fromConfig_openaiCompatible_returnsOpenAiClient() {
    AletheiaConfig cfg = AletheiaConfig.of("openai_compatible", null, "key", "model", 0.0);
    LlmClient client = LlmClientFactory.fromConfig(cfg);
    assertThat(client).isInstanceOf(OpenAiCompatibleClient.class);
  }

  @Test
  void fromConfig_anthropic_returnsAnthropicClient() {
    AletheiaConfig cfg =
        AletheiaConfig.of("anthropic", null, "sk-ant-test", "claude-3-5-haiku-20241022", 0.0);
    LlmClient client = LlmClientFactory.fromConfig(cfg);
    assertThat(client).isInstanceOf(AnthropicClient.class);
    assertThat(client.providerId()).isEqualTo("anthropic");
  }

  @Test
  void fromConfig_openaiMissingKey_throwsMissingCredentials() {
    AletheiaConfig cfg = AletheiaConfig.of("openai", null, null, "gpt-4o-mini", 0.0);
    assertThatThrownBy(() -> LlmClientFactory.fromConfig(cfg))
        .isInstanceOf(MissingCredentialsException.class)
        .hasMessageContaining("LLM_API_KEY")
        .hasMessageContaining("openai");
  }

  @Test
  void fromConfig_anthropicBlankKey_throwsMissingCredentials() {
    AletheiaConfig cfg =
        AletheiaConfig.of("anthropic", null, "  ", "claude-3-5-haiku-20241022", 0.0);
    assertThatThrownBy(() -> LlmClientFactory.fromConfig(cfg))
        .isInstanceOf(MissingCredentialsException.class)
        .hasMessageContaining("LLM_API_KEY");
  }

  @Test
  void fromConfig_unknownProvider_throwsIllegalArgument() {
    AletheiaConfig cfg = AletheiaConfig.of("gopher", null, "key", "model", 0.0);
    assertThatThrownBy(() -> LlmClientFactory.fromConfig(cfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("gopher");
  }
}
