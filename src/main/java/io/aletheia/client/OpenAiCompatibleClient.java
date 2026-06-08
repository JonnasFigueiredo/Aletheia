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
import java.util.Optional;

/**
 * LLM client for OpenAI and OpenAI-compatible APIs (e.g. Azure OpenAI, local LLM proxies). Retries
 * on 429, 5xx and transport failures using {@link RetryPolicies}.
 */
public final class OpenAiCompatibleClient implements LlmClient {

  private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final AletheiaConfig config;
  private final String baseUrl;
  private final HttpClient http;
  private final Retry retry;

  public OpenAiCompatibleClient(AletheiaConfig config) {
    this(
        config,
        RetryPolicies.defaultRetry("openai-" + System.identityHashCode(config)),
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
            .build());
  }

  /** Package-private constructor for tests (injectable retry and HttpClient). */
  OpenAiCompatibleClient(AletheiaConfig config, Retry retry, HttpClient http) {
    this.config = config;
    this.baseUrl =
        config.getBaseUrl() != null && !config.getBaseUrl().isBlank()
            ? config.getBaseUrl()
            : DEFAULT_BASE_URL;
    this.retry = retry;
    this.http = http;
  }

  @Override
  public LlmResponse complete(LlmRequest request) {
    return Retry.decorateSupplier(retry, () -> doComplete(request)).get();
  }

  private LlmResponse doComplete(LlmRequest request) {
    try {
      Map<String, Object> body = buildCompletionBody(request);
      String jsonBody = MAPPER.writeValueAsString(body);

      HttpRequest httpReq =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + "/chat/completions"))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + config.getApiKey())
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
              .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
              .build();

      long start = System.currentTimeMillis();
      HttpResponse<String> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
      long elapsed = System.currentTimeMillis() - start;

      checkStatus(resp.statusCode(), resp.body());
      return parseCompletionResponse(resp.body(), elapsed);
    } catch (LlmHttpException e) {
      throw e;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new LlmHttpException(-1, "Transport error: " + e.getMessage());
    }
  }

  @Override
  public Optional<double[]> embed(String text) {
    try {
      return Retry.decorateSupplier(retry, () -> doEmbed(text)).get();
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private Optional<double[]> doEmbed(String text) {
    try {
      Map<String, Object> body = Map.of("model", config.getEmbedModel(), "input", text);
      String jsonBody = MAPPER.writeValueAsString(body);

      HttpRequest httpReq =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + "/embeddings"))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + config.getApiKey())
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
              .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
              .build();

      HttpResponse<String> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());

      if (resp.statusCode() == 404 || resp.statusCode() >= 400) {
        return Optional.empty();
      }

      JsonNode root = MAPPER.readTree(resp.body());
      JsonNode embedding = root.get("data").get(0).get("embedding");
      double[] vector = new double[embedding.size()];
      for (int i = 0; i < embedding.size(); i++) {
        vector[i] = embedding.get(i).asDouble();
      }
      return Optional.of(vector);
    } catch (LlmHttpException e) {
      return Optional.empty();
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }

  @Override
  public String providerId() {
    return "openai";
  }

  private Map<String, Object> buildCompletionBody(LlmRequest request) {
    String model =
        request.getModel() != null
            ? request.getModel()
            : (config.getModel() != null ? config.getModel() : "gpt-4o-mini");
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    List<Map<String, String>> messages = new ArrayList<>();
    if (request.getSystem() != null) {
      messages.add(Map.of("role", "system", "content", request.getSystem()));
    }
    messages.add(Map.of("role", "user", "content", request.getPrompt()));
    body.put("messages", messages);
    body.put("temperature", request.getTemperature());
    if (request.getMaxTokens() > 0) {
      body.put("max_tokens", request.getMaxTokens());
    }
    return body;
  }

  private static LlmResponse parseCompletionResponse(String responseBody, long latencyMs) {
    try {
      JsonNode root = MAPPER.readTree(responseBody);
      String text = root.get("choices").get(0).get("message").get("content").asText();
      int tokensIn = root.get("usage").get("prompt_tokens").asInt();
      int tokensOut = root.get("usage").get("completion_tokens").asInt();
      return new LlmResponse(text, tokensIn, tokensOut, latencyMs);
    } catch (IOException e) {
      throw new LlmHttpException(-1, "Failed to parse response: " + e.getMessage());
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
