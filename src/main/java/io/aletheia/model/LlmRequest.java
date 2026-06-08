package io.aletheia.model;

import java.util.Objects;

/** Outbound request to an LLM provider. */
public final class LlmRequest {

  private final String prompt;
  private final String system;
  private final String provider;
  private final String model;
  private final double temperature;
  private final int maxTokens;
  private final String caseId;

  private LlmRequest(Builder builder) {
    this.prompt = Objects.requireNonNull(builder.prompt, "prompt must not be null");
    this.system = builder.system;
    this.provider = builder.provider;
    this.model = builder.model;
    this.temperature = builder.temperature;
    this.maxTokens = builder.maxTokens;
    this.caseId = builder.caseId;
  }

  public String getPrompt() {
    return prompt;
  }

  public String getSystem() {
    return system;
  }

  public String getProvider() {
    return provider;
  }

  public String getModel() {
    return model;
  }

  public double getTemperature() {
    return temperature;
  }

  public int getMaxTokens() {
    return maxTokens;
  }

  /** Optional case identifier used by {@code MockLlmClient} for exact fixture matching. */
  public String getCaseId() {
    return caseId;
  }

  public static Builder builder(String prompt) {
    return new Builder(prompt);
  }

  public static final class Builder {
    private final String prompt;
    private String system;
    private String provider;
    private String model;
    private double temperature = 0.0;
    private int maxTokens = 1024;
    private String caseId;

    private Builder(String prompt) {
      this.prompt = prompt;
    }

    public Builder system(String system) {
      this.system = system;
      return this;
    }

    public Builder provider(String provider) {
      this.provider = provider;
      return this;
    }

    public Builder model(String model) {
      this.model = model;
      return this;
    }

    public Builder temperature(double temperature) {
      this.temperature = temperature;
      return this;
    }

    public Builder maxTokens(int maxTokens) {
      this.maxTokens = maxTokens;
      return this;
    }

    public Builder caseId(String caseId) {
      this.caseId = caseId;
      return this;
    }

    public LlmRequest build() {
      return new LlmRequest(this);
    }
  }

  @Override
  public String toString() {
    return "LlmRequest{provider='"
        + provider
        + "', model='"
        + model
        + "', prompt='"
        + prompt
        + "'}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LlmRequest)) return false;
    LlmRequest that = (LlmRequest) o;
    return Double.compare(temperature, that.temperature) == 0
        && maxTokens == that.maxTokens
        && Objects.equals(prompt, that.prompt)
        && Objects.equals(system, that.system)
        && Objects.equals(provider, that.provider)
        && Objects.equals(model, that.model);
  }

  @Override
  public int hashCode() {
    return Objects.hash(prompt, system, provider, model, temperature, maxTokens);
  }
}
