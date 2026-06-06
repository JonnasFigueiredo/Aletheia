package io.aletheia.config;

import java.util.Objects;

/** Reads Aletheia runtime configuration from environment variables. */
public final class AletheiaConfig {

  private final String provider;
  private final String baseUrl;
  private final String apiKey;
  private final String model;
  private final double temperature;

  private AletheiaConfig(
      String provider, String baseUrl, String apiKey, String model, double temperature) {
    this.provider = provider;
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.model = model;
    this.temperature = temperature;
  }

  /** Reads configuration from environment variables. Never throws for optional missing vars. */
  public static AletheiaConfig fromEnvironment() {
    String provider = getEnv("LLM_PROVIDER", "mock");
    String baseUrl = getEnv("LLM_BASE_URL", null);
    String apiKey = getEnv("LLM_API_KEY", null);
    String model = getEnv("LLM_MODEL", null);
    double temperature = parseTemperature(getEnv("ALETHEIA_TEMPERATURE", "0"));
    return new AletheiaConfig(provider, baseUrl, apiKey, model, temperature);
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

  @Override
  public String toString() {
    return "AletheiaConfig{provider='" + provider + "', model='" + model + "'}";
  }
}
