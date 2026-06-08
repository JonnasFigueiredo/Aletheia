package io.aletheia.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aletheia.config.AletheiaConfig;
import io.aletheia.model.LlmRequest;
import io.aletheia.model.LlmResponse;
import io.github.resilience4j.retry.Retry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM client for the Anthropic Messages API. Retries on 429, 5xx, and transport failures using
 * {@link RetryPolicies}. Embedding is not supported ({@code embed()} always returns empty).
 */
public final class AnthropicClient implements LlmClient {

  static final String DEFAULT_BASE_URL = "https://api.anthropic.com/v1/messages";
  private static final String ANTHROPIC_VERSION = "2023-06-01";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final AletheiaConfig config;
  private final String baseUrl;
  private final HttpClient http;
  private final Retry retry;

  public AnthropicClient(AletheiaConfig config) {
    this(
        config,
        RetryPolicies.defaultRetry("anthropic-" + System.identityHashCode(config)),
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
            .build(),
        DEFAULT_BASE_URL);
  }

  /** Package-private constructor for tests (injectable retry, HttpClient, and URL). */
  AnthropicClient(AletheiaConfig config, Retry retry, HttpClient http, String baseUrl) {
    this.config = config;
    this.retry = retry;
    this.http = http;
    this.baseUrl = baseUrl;
  }

  @Override
  public LlmResponse complete(LlmRequest request) {
    return Retry.decorateSupplier(retry, () -> doComplete(request)).get();
  }

  private LlmResponse doComplete(LlmRequest request) {
    try {
      Map<String, Object> body = buildBody(request);
      String jsonBody = MAPPER.writeValueAsString(body);

      HttpRequest httpReq =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl))
              .header("x-api-key", config.getApiKey())
              .header("anthropic-version", ANTHROPIC_VERSION)
              .header("content-type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
              .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
              .build();

      long start = System.currentTimeMillis();
      HttpResponse<String> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
      long elapsed = System.currentTimeMillis() - start;

      checkStatus(resp.statusCode(), resp.body());
      return parseResponse(resp.body(), elapsed);
    } catch (LlmHttpException e) {
      throw e;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new LlmHttpException(-1, "Transport error: " + e.getMessage());
    }
  }

  @Override
  public String providerId() {
    return "anthropic";
  }

  private Map<String, Object> buildBody(LlmRequest request) {
    String model =
        request.getModel() != null
            ? request.getModel()
            : (config.getModel() != null ? config.getModel() : "claude-3-5-haiku-20241022");
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("max_tokens", request.getMaxTokens() > 0 ? request.getMaxTokens() : 1024);
    if (request.getSystem() != null) {
      body.put("system", request.getSystem());
    }
    List<Map<String, String>> messages = new ArrayList<>();
    messages.add(Map.of("role", "user", "content", request.getPrompt()));
    body.put("messages", messages);
    body.put("temperature", request.getTemperature());
    return body;
  }

  private static LlmResponse parseResponse(String responseBody, long latencyMs) {
    try {
      JsonNode root = MAPPER.readTree(responseBody);
      String text = root.get("content").get(0).get("text").asText();
      int tokensIn = root.get("usage").get("input_tokens").asInt();
      int tokensOut = root.get("usage").get("output_tokens").asInt();
      return new LlmResponse(text, tokensIn, tokensOut, latencyMs);
    } catch (IOException e) {
      throw new LlmHttpException(-1, "Failed to parse Anthropic response: " + e.getMessage());
    }
  }

  private static void checkStatus(int statusCode, String body) {
    if (statusCode >= 400) {
      throw new LlmHttpException(statusCode, "HTTP " + statusCode + ": " + truncate(body, 200));
    }
  }

  private static String truncate(String s, int maxLen) {
    return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "…" : s;
  }
}
