package io.aletheia.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** Response received from an LLM provider. */
public final class LlmResponse {

  private final String text;
  private final int tokensIn;
  private final int tokensOut;
  private final long latencyMs;
  private final Map<String, Object> raw;

  public LlmResponse(String text, int tokensIn, int tokensOut, long latencyMs) {
    this(text, tokensIn, tokensOut, latencyMs, null);
  }

  public LlmResponse(
      String text, int tokensIn, int tokensOut, long latencyMs, Map<String, Object> raw) {
    this.text = Objects.requireNonNull(text, "text must not be null");
    this.tokensIn = tokensIn;
    this.tokensOut = tokensOut;
    this.latencyMs = latencyMs;
    this.raw = raw == null ? Collections.emptyMap() : Collections.unmodifiableMap(raw);
  }

  public String getText() {
    return text;
  }

  public int getTokensIn() {
    return tokensIn;
  }

  public int getTokensOut() {
    return tokensOut;
  }

  public long getLatencyMs() {
    return latencyMs;
  }

  /** Raw response payload from the provider, keyed by provider-specific field names. */
  public Map<String, Object> getRaw() {
    return raw;
  }

  @Override
  public String toString() {
    return "LlmResponse{tokensIn="
        + tokensIn
        + ", tokensOut="
        + tokensOut
        + ", latencyMs="
        + latencyMs
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LlmResponse)) return false;
    LlmResponse that = (LlmResponse) o;
    return tokensIn == that.tokensIn
        && tokensOut == that.tokensOut
        && latencyMs == that.latencyMs
        && Objects.equals(text, that.text)
        && Objects.equals(raw, that.raw);
  }

  @Override
  public int hashCode() {
    return Objects.hash(text, tokensIn, tokensOut, latencyMs, raw);
  }
}
