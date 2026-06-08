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

/** Unit tests for {@link AnthropicClient} using an in-process HTTP server. */
class AnthropicClientTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private HttpServer server;
  private AnthropicClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.start();
    int port = server.getAddress().getPort();
    String messagesUrl = "http://localhost:" + port + "/v1/messages";

    AletheiaConfig config =
        AletheiaConfig.of("anthropic", null, "sk-ant-test", "claude-3-5-haiku-20241022", 0.0);
    client =
        new AnthropicClient(
            config,
            RetryPolicies.noWaitRetry("test-anthropic"),
            HttpClient.newBuilder().build(),
            messagesUrl);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void complete_requestBodyShape_systemIsTopLevel() throws IOException {
    AtomicReference<JsonNode> captured = new AtomicReference<>();
    AtomicReference<String> capturedApiKey = new AtomicReference<>();
    AtomicReference<String> capturedVersion = new AtomicReference<>();

    server.createContext(
        "/v1/messages",
        exchange -> {
          capturedApiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
          capturedVersion.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          captured.set(MAPPER.readTree(body));
          sendJson(
              exchange,
              200,
              "{\"content\":[{\"type\":\"text\",\"text\":\"Paris\"}],"
                  + "\"usage\":{\"input_tokens\":12,\"output_tokens\":3}}");
        });

    LlmRequest req =
        LlmRequest.builder("What is the capital of France?")
            .system("You are a geography expert.")
            .build();
    LlmResponse resp = client.complete(req);

    assertThat(resp.getText()).isEqualTo("Paris");
    assertThat(resp.getTokensIn()).isEqualTo(12);
    assertThat(resp.getTokensOut()).isEqualTo(3);

    assertThat(capturedApiKey.get()).isEqualTo("sk-ant-test");
    assertThat(capturedVersion.get()).isEqualTo("2023-06-01");

    JsonNode node = captured.get();
    // System is a top-level field, not inside messages
    assertThat(node.get("system").asText()).isEqualTo("You are a geography expert.");
    assertThat(node.get("messages")).hasSize(1);
    assertThat(node.get("messages").get(0).get("role").asText()).isEqualTo("user");
    assertThat(node.get("messages").get(0).get("content").asText())
        .isEqualTo("What is the capital of France?");
    assertThat(node.has("max_tokens")).isTrue();
  }

  @Test
  void complete_noSystem_omitsSystemField() throws IOException {
    AtomicReference<JsonNode> captured = new AtomicReference<>();
    server.createContext(
        "/v1/messages",
        exchange -> {
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          captured.set(MAPPER.readTree(body));
          sendJson(
              exchange,
              200,
              "{\"content\":[{\"type\":\"text\",\"text\":\"Hi\"}],"
                  + "\"usage\":{\"input_tokens\":3,\"output_tokens\":2}}");
        });

    client.complete(LlmRequest.builder("Hello").build());
    assertThat(captured.get().has("system")).isFalse();
  }

  @Test
  void complete_retryOn503_succeedsOnSecondAttempt() {
    AtomicInteger attempts = new AtomicInteger(0);
    server.createContext(
        "/v1/messages",
        exchange -> {
          if (attempts.incrementAndGet() == 1) {
            sendText(exchange, 503, "Service Unavailable");
          } else {
            sendJson(
                exchange,
                200,
                "{\"content\":[{\"type\":\"text\",\"text\":\"OK\"}],"
                    + "\"usage\":{\"input_tokens\":3,\"output_tokens\":2}}");
          }
        });

    LlmResponse resp = client.complete(LlmRequest.builder("test").build());
    assertThat(resp.getText()).isEqualTo("OK");
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  void complete_failFastOn400_doesNotRetry() {
    AtomicInteger attempts = new AtomicInteger(0);
    server.createContext(
        "/v1/messages",
        exchange -> {
          attempts.incrementAndGet();
          sendText(exchange, 400, "{\"error\":{\"type\":\"invalid_request_error\"}}");
        });

    assertThatThrownBy(() -> client.complete(LlmRequest.builder("bad").build()))
        .isInstanceOf(LlmHttpException.class)
        .satisfies(e -> assertThat(((LlmHttpException) e).getStatusCode()).isEqualTo(400));
    assertThat(attempts.get()).isEqualTo(1);
  }

  @Test
  void embed_alwaysReturnsEmpty() {
    assertThat(client.embed("anything")).isEmpty();
  }

  @Test
  void providerId_returnsAnthropic() {
    assertThat(client.providerId()).isEqualTo("anthropic");
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
