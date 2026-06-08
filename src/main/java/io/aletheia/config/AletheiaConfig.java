package io.aletheia.config;

import java.util.Objects;

/** Reads Aletheia runtime configuration from environment variables. */
public final class AletheiaConfig {

  private final String provider;
  private final String baseUrl;
  private final String apiKey;
  private final String model;
  private final double temperature;
  private final String embedModel;
  private final String fixturePath;
  private final int timeoutSeconds;

  private AletheiaConfig(
      String provider,
      String baseUrl,
      String apiKey,
      String model,
      double temperature,
      String embedModel,
      String fixturePath,
      int timeoutSeconds) {
    this.provider = provider;
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.model = model;
    this.temperature = temperature;
    this.embedModel = embedModel;
    this.fixturePath = fixturePath;
    this.timeoutSeconds = timeoutSeconds;
  }

  /** Constructs a config with explicit values; useful for tests. */
  public static AletheiaConfig of(
      String provider,
      String baseUrl,
      String apiKey,
      String model,
      double temperature,
      String embedModel,
      String fixturePath,
      int timeoutSeconds) {
    return new AletheiaConfig(
        provider, baseUrl, apiKey, model, temperature, embedModel, fixturePath, timeoutSeconds);
  }

  /** Constructs a config with common defaults for {@code embedModel} and {@code fixturePath}. */
  public static AletheiaConfig of(
      String provider, String baseUrl, String apiKey, String model, double temperature) {
    return new AletheiaConfig(
        provider,
        baseUrl,
        apiKey,
        model,
        temperature,
        "text-embedding-3-small",
        "fixtures/mock-responses.yaml",
        30);
  }

  /** Reads configuration from environment variables. Never throws for optional missing vars. */
  public static AletheiaConfig fromEnvironment() {
    String provider = getEnv("LLM_PROVIDER", "mock");
    String baseUrl = getEnv("LLM_BASE_URL", null);
    String apiKey = getEnv("LLM_API_KEY", null);
    String model = getEnv("LLM_MODEL", null);
    double temperature = parseTemperature(getEnv("ALETHEIA_TEMPERATURE", "0"));
    String embedModel = getEnv("EMBED_MODEL", "text-embedding-3-small");
    String fixturePath = getEnv("ALETHEIA_FIXTURE_PATH", "fixtures/mock-responses.yaml");
    int timeoutSeconds = parseInt(getEnv("LLM_TIMEOUT_SECONDS", "30"), 30);
    return new AletheiaConfig(
        provider, baseUrl, apiKey, model, temperature, embedModel, fixturePath, timeoutSeconds);
  }

  private static String getEnv(String name, String defaultValue) {
    String value = System.getenv(name);
    return (value != null && !value.isBlank()) ? value : defaultValue;
  }

  private static double parseTemperature(String raw) {
    try {
      return Double.parseDouble(Objects.requireNonNullElse(raw, "0"));
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }

  private static int parseInt(String raw, int defaultValue) {
    try {
      return Integer.parseInt(Objects.requireNonNullElse(raw, String.valueOf(defaultValue)));
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  public String getProvider() {
    return provider;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public String getApiKey() {
    return apiKey;
  }

  public String getModel() {
    return model;
  }

  public double getTemperature() {
    return temperature;
  }

  /** Embedding model identifier for OpenAI-compatible providers. */
  public String getEmbedModel() {
    return embedModel;
  }

  /** Path to the mock fixture YAML file, resolved against the working directory. */
  public String getFixturePath() {
    return fixturePath;
  }

  /** HTTP timeout in seconds for real provider clients. */
  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }

  @Override
  public String toString() {
    return "AletheiaConfig{provider='" + provider + "', model='" + model + "'}";
  }
}
