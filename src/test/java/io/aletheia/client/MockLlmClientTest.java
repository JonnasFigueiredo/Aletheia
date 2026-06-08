package io.aletheia.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aletheia.model.LlmRequest;
import io.aletheia.model.LlmResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link MockLlmClient}. */
class MockLlmClientTest {

  private static final String FIXTURE_YAML =
      "- id: case-id-001\n"
          + "  match: capital of France\n"
          + "  response: \"Paris (matched by id)\"\n"
          + "  tokens_in: 10\n"
          + "  tokens_out: 5\n"
          + "  latency_ms: 100\n"
          + "  raw:\n"
          + "    model: mock\n"
          + "- id: case-other-001\n"
          + "  match: capital of France\n"
          + "  response: \"Paris (matched by substring)\"\n"
          + "  tokens_in: 10\n"
          + "  tokens_out: 5\n"
          + "  latency_ms: 50\n";

  private Path writeFixture(Path dir, String yaml) throws IOException {
    Path file = dir.resolve("mock-responses.yaml");
    Files.writeString(file, yaml);
    return file;
  }

  @Test
  void complete_substringMatch_returnsCorrectResponse(@TempDir Path tmp) throws IOException {
    Path fixture =
        writeFixture(
            tmp,
            "- id: test-001\n"
                + "  match: capital of France\n"
                + "  response: \"Paris\"\n"
                + "  tokens_in: 10\n"
                + "  tokens_out: 5\n"
                + "  latency_ms: 100\n");
    MockLlmClient client = new MockLlmClient(fixture);

    LlmResponse resp =
        client.complete(LlmRequest.builder("What is the capital of France?").build());

    assertThat(resp.getText()).isEqualTo("Paris");
    assertThat(resp.getTokensIn()).isEqualTo(10);
    assertThat(resp.getTokensOut()).isEqualTo(5);
    assertThat(resp.getLatencyMs()).isEqualTo(100);
  }

  @Test
  void complete_idMatchWinsOverSubstringMatch(@TempDir Path tmp) throws IOException {
    Path fixture = writeFixture(tmp, FIXTURE_YAML);
    MockLlmClient client = new MockLlmClient(fixture);

    LlmRequest requestWithCaseId =
        LlmRequest.builder("What is the capital of France?").caseId("case-id-001").build();
    LlmResponse resp = client.complete(requestWithCaseId);

    assertThat(resp.getText()).isEqualTo("Paris (matched by id)");
  }

  @Test
  void complete_substringFallbackWhenNoCaseId(@TempDir Path tmp) throws IOException {
    Path fixture = writeFixture(tmp, FIXTURE_YAML);
    MockLlmClient client = new MockLlmClient(fixture);

    LlmRequest requestNoCaseId = LlmRequest.builder("What is the capital of France?").build();
    LlmResponse resp = client.complete(requestNoCaseId);

    // No caseId → falls through to first substring match (case-id-001 has match: capital of France)
    assertThat(resp.getText()).isEqualTo("Paris (matched by id)");
  }

  @Test
  void complete_noMatchThrowsWithPromptPrefix(@TempDir Path tmp) throws IOException {
    Path fixture =
        writeFixture(
            tmp,
            "- id: test-001\n"
                + "  match: capital of France\n"
                + "  response: \"Paris\"\n"
                + "  tokens_in: 10\n"
                + "  tokens_out: 5\n"
                + "  latency_ms: 100\n");
    MockLlmClient client = new MockLlmClient(fixture);

    assertThatThrownBy(
            () -> client.complete(LlmRequest.builder("Tell me about quantum physics").build()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("quantum physics");
  }

  @Test
  void complete_deterministicAcrossMultipleCalls(@TempDir Path tmp) throws IOException {
    Path fixture =
        writeFixture(
            tmp,
            "- id: test-001\n"
                + "  match: hello\n"
                + "  response: \"Hi there!\"\n"
                + "  tokens_in: 3\n"
                + "  tokens_out: 4\n"
                + "  latency_ms: 20\n");
    MockLlmClient client = new MockLlmClient(fixture);

    LlmRequest req = LlmRequest.builder("Say hello").build();
    LlmResponse r1 = client.complete(req);
    LlmResponse r2 = client.complete(req);
    LlmResponse r3 = client.complete(req);

    assertThat(r1).isEqualTo(r2);
    assertThat(r2).isEqualTo(r3);
  }

  @Test
  void embed_alwaysReturnsEmpty(@TempDir Path tmp) throws IOException {
    Path fixture =
        writeFixture(
            tmp,
            "- id: test-001\n"
                + "  match: test\n"
                + "  response: \"ok\"\n"
                + "  tokens_in: 1\n"
                + "  tokens_out: 1\n"
                + "  latency_ms: 1\n");
    MockLlmClient client = new MockLlmClient(fixture);

    assertThat(client.embed("some text")).isEmpty();
  }

  @Test
  void providerId_returnsMock(@TempDir Path tmp) throws IOException {
    Path fixture =
        writeFixture(
            tmp,
            "- id: t\n"
                + "  match: x\n"
                + "  response: y\n"
                + "  tokens_in: 1\n"
                + "  tokens_out: 1\n"
                + "  latency_ms: 1\n");
    assertThat(new MockLlmClient(fixture).providerId()).isEqualTo("mock");
  }

  @Test
  void constructor_missingFixtureFile_throwsIllegalArgument(@TempDir Path tmp) {
    assertThatThrownBy(() -> new MockLlmClient(tmp.resolve("does-not-exist.yaml")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does-not-exist.yaml");
  }
}
