package io.aletheia.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.aletheia.client.MockLlmClient;
import io.aletheia.model.EvalCase;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.runner.EvalRunner;
import io.aletheia.runner.RunSummary;
import io.aletheia.runner.baseline.Regression;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonReporterTest {

  private static final MockLlmClient CLIENT =
      new MockLlmClient(Path.of("fixtures/mock-responses.yaml"));

  @TempDir Path tempDir;

  private RunSummary buildSummary() {
    EvaluatorSpec spec = new EvaluatorSpec();
    spec.setType("contains");
    spec.addParam("must_include", List.of("contains-target"));

    EvalCase pass = new EvalCase();
    pass.setId("runner-pass-001");
    pass.setPrompt("runner-pass");
    pass.setEvaluators(List.of(spec));

    EvalCase fail = new EvalCase();
    fail.setId("runner-fail-001");
    fail.setPrompt("runner-fail");
    fail.setEvaluators(List.of(spec));

    return EvalRunner.runSuite(List.of(pass, fail), CLIENT);
  }

  @Test
  void outputFileCreated() throws IOException {
    Path out = tempDir.resolve("summary.json");
    JsonReporter.write(buildSummary(), out);
    assertThat(out).exists();
  }

  @Test
  void outputContainsTotals() throws IOException {
    Path out = tempDir.resolve("summary.json");
    JsonReporter.write(buildSummary(), out);

    String json = Files.readString(out);
    assertThat(json).contains("\"totals\"");
    assertThat(json).contains("\"cases\" : 2");
    assertThat(json).contains("\"passed\" : 1");
    assertThat(json).contains("\"failed\" : 1");
  }

  @Test
  void outputContainsEvaluatorStats() throws IOException {
    Path out = tempDir.resolve("summary.json");
    JsonReporter.write(buildSummary(), out);

    String json = Files.readString(out);
    assertThat(json).contains("\"perEvaluator\"");
    assertThat(json).contains("\"contains\"");
  }

  @Test
  void baselineDiffSectionRenderedWhenPresent() throws IOException {
    Path out = tempDir.resolve("summary.json");
    RunSummary summary = buildSummary();
    summary.setBaselineDiff(List.of(new Regression("runner-fail-001", "contains", 1.0, 0.0)));

    JsonReporter.write(summary, out);

    String json = Files.readString(out);
    assertThat(json).contains("\"baselineDiff\"");
    assertThat(json).contains("runner-fail-001");
  }

  @Test
  void baselineDiffSectionAbsentWhenNotSet() throws IOException {
    Path out = tempDir.resolve("summary.json");
    JsonReporter.write(buildSummary(), out);

    String json = Files.readString(out);
    assertThat(json).doesNotContain("baselineDiff");
  }

  @Test
  void createsParentDirectories() throws IOException {
    Path out = tempDir.resolve("nested/dir/summary.json");
    JsonReporter.write(buildSummary(), out);
    assertThat(out).exists();
  }
}
