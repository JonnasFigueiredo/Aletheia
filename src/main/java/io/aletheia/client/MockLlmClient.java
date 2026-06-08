package io.aletheia.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.aletheia.model.LlmRequest;
import io.aletheia.model.LlmResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic in-memory LLM client for CI. Loads response fixtures from a YAML file at
 * construction time. No network calls are made.
 */
public final class MockLlmClient implements LlmClient {

  private static final Path DEFAULT_FIXTURE = Path.of("fixtures", "mock-responses.yaml");

  private final List<FixtureEntry> entries;

  /** Loads fixtures from the default path ({@code fixtures/mock-responses.yaml}). */
  public MockLlmClient() {
    this(DEFAULT_FIXTURE);
  }

  /** Loads fixtures from {@code fixturePath}. */
  public MockLlmClient(Path fixturePath) {
    try {
      ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
      this.entries =
          Collections.unmodifiableList(
              yaml.readValue(fixturePath.toFile(), new TypeReference<List<FixtureEntry>>() {}));
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to load mock fixture file: " + fixturePath, e);
    }
  }

  @Override
  public LlmResponse complete(LlmRequest request) {
    String caseId = request.getCaseId();
    String prompt = request.getPrompt();

    // First pass: exact id-match (highest priority)
    if (caseId != null) {
      for (FixtureEntry entry : entries) {
        if (caseId.equals(entry.id)) {
          return toResponse(entry);
        }
      }
    }

    // Second pass: substring match
    for (FixtureEntry entry : entries) {
      if (entry.match != null && prompt.contains(entry.match)) {
        return toResponse(entry);
      }
    }

    String prefix = prompt.length() > 60 ? prompt.substring(0, 60) + "..." : prompt;
    throw new IllegalArgumentException("No fixture match for prompt: \"" + prefix + "\"");
  }

  @Override
  public Optional<double[]> embed(String text) {
    return Optional.empty();
  }

  @Override
  public String providerId() {
    return "mock";
  }

  private static LlmResponse toResponse(FixtureEntry entry) {
    return new LlmResponse(
        entry.response, entry.tokensIn, entry.tokensOut, entry.latencyMs, entry.raw);
  }

  static final class FixtureEntry {

    public String id;
    public String match;
    public String response;

    @JsonProperty("tokens_in")
    public int tokensIn;

    @JsonProperty("tokens_out")
    public int tokensOut;

    @JsonProperty("latency_ms")
    public int latencyMs;

    public Map<String, Object> raw;
  }
}
