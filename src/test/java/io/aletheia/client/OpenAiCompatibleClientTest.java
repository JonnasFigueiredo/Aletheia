package io.aletheia.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.aletheia.config.AletheiaConfig;
import io.aletheia.model.LlmRequest;
import io.aletheia.model.LlmResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link OpenAiCompatibleClient} using an in-process HTTP server. */
class OpenAiCompatibleClientTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private HttpServer server;
  private String baseUrl;
  private AletheiaConfig config;
  private OpenAiCompatibleClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.start();
    int port = server.getAddress().getPort();
    baseUrl = "http://localhost:" + port + "/v1";
    config = AletheiaConfig.of("openai", baseUrl, "test-key", "gpt-4o-mini", 0.0);
    client =
        new OpenAiCompatibleClient(
            config, RetryPolicies.noWaitRetry("test-openai"), HttpClient.newBuilder().build());
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void complete_requestBodyShape_systemAndMessages() throws IOException {
    AtomicReference<JsonNode> captured = new AtomicReference<>();
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          captured.set(MAPPER.readTree(body));
          sendJson(
              exchange,
              200,
              "{\"choices\":[{\"message\":{\"content\":\"Paris\"}}],"
                  + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}");
        });

    LlmRequest req =
        LlmRequest.builder("What is the capital of France?")
            .system("You are a geography expert.")
            .temperature(0.5)
            .build();
    LlmResponse resp = client.complete(req);

    assertThat(resp.getText()).isEqualTo("Paris");
    assertThat(resp.getTokensIn()).isEqualTo(10);
    assertThat(resp.getTokensOut()).isEqualTo(5);

    JsonNode node = captured.get();
    assertThat(node.get("model").asText()).isEqualTo("gpt-4o-mini");
    assertThat(node.get("temperature").asDouble()).isEqualTo(0.5);
    assertThat(node.get("messages").get(0).get("role").asText()).isEqualTo("system");
    assertThat(node.get("messages").get(1).get("role").asText()).isEqualTo("user");
    assertThat(node.get("messages").get(1).get("content").asText())
        .isEqualTo("What is the capital of France?");
  }

  @Test
  void complete_noSystemMessage_onlyUserMessage() throws IOException {
    AtomicReference<JsonNode> captured = new AtomicReference<>();
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          captured.set(MAPPER.readTree(body));
          sendJson(
              exchange,
              200,
              "{\"choices\":[{\"message\":{\"content\":\"OK\"}}],"
                  + "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":2}}");
        });

    client.complete(LlmRequest.builder("Hello").build());

    assertThat(captured.get().get("messages")).hasSize(1);
    assertThat(captured.get().get("messages").get(0).get("role").asText()).isEqualTo("user");
  }

  @Test
  void complete_retryOn503_succeedsOnSecondAttempt() {
    AtomicInteger attempts = new AtomicInteger(0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          if (attempts.incrementAndGet() == 1) {
            sendText(exchange, 503, "Service Unavailable");
          } else {
            sendJson(
                exchange,
                200,
                "{\"choices\":[{\"message\":{\"content\":\"OK\"}}],"
                    + "\"usage\":{\"prompt_tokens\":4,\"completion_tokens\":2}}");
          }
        });

    LlmResponse resp = client.complete(LlmRequest.builder("test").build());
    assertThat(resp.getText()).isEqualTo("OK");
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  void complete_failFastOn401() {
    server.createContext(
        "/v1/chat/completions",
        exchange -> sendText(exchange, 401, "{\"error\":\"Unauthorized\"}"));

    assertThatThrownBy(() -> client.complete(LlmRequest.builder("test").build()))
        .isInstanceOf(LlmHttpException.class)
        .satisfies(e -> assertThat(((LlmHttpException) e).getStatusCode()).isEqualTo(401));
  }

  @Test
  void complete_failFastOn400() {
    AtomicInteger attempts = new AtomicInteger(0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          attempts.incrementAndGet();
          sendText(exchange, 400, "{\"error\":\"Bad Request\"}");
        });

    assertThatThrownBy(() -> client.complete(LlmRequest.builder("bad request").build()))
        .isInstanceOf(LlmHttpException.class)
        .satisfies(e -> assertThat(((LlmHttpException) e).getStatusCode()).isEqualTo(400));
    // Must not retry on 400
    assertThat(attempts.get()).isEqualTo(1);
  }

  @Test
  void embed_returnsVector() throws IOException {
    server.createContext(
        "/v1/embeddings",
        exchange -> sendJson(exchange, 200, "{\"data\":[{\"embedding\":[0.1,0.2,0.3]}]}"));

    assertThat(client.embed("hello")).isPresent();
    double[] vector = client.embed("hello").get();
    assertThat(vector).hasSize(3);
  }

  @Test
  void embed_returnsEmptyOn404() throws IOException {
    server.createContext("/v1/embeddings", exchange -> sendText(exchange, 404, "Not Found"));

    assertThat(client.embed("hello")).isEmpty();
  }

  @Test
  void providerId_returnsOpenai() {
    assertThat(client.providerId()).isEqualTo("openai");
  }

  private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  private static void sendText(HttpExchange exchange, int status, String text) {
    try {
      byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(bytes);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
