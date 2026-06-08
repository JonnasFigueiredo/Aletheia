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

class HtmlReporterTest {

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
    Path out = tempDir.resolve("summary.html");
    HtmlReporter.write(buildSummary(), out);
    assertThat(out).exists();
  }

  @Test
  void outputIsValidHtml() throws IOException {
    Path out = tempDir.resolve("summary.html");
    HtmlReporter.write(buildSummary(), out);

    String html = Files.readString(out);
    assertThat(html).startsWith("<!DOCTYPE html>");
    assertThat(html).contains("<html");
    assertThat(html).endsWith("</html>\n");
  }

  @Test
  void outputContainsCaseCounts() throws IOException {
    Path out = tempDir.resolve("summary.html");
    HtmlReporter.write(buildSummary(), out);

    String html = Files.readString(out);
    assertThat(html).contains("runner-pass-001");
    assertThat(html).contains("runner-fail-001");
    assertThat(html).contains("PASS");
    assertThat(html).contains("FAIL");
  }

  @Test
  void baselineDiffSectionRenderedWhenPresent() throws IOException {
    Path out = tempDir.resolve("summary.html");
    RunSummary summary = buildSummary();
    summary.setBaselineDiff(List.of(new Regression("runner-fail-001", "contains", 1.0, 0.0)));

    HtmlReporter.write(summary, out);

    String html = Files.readString(out);
    assertThat(html).contains("Baseline Regressions");
    assertThat(html).contains("runner-fail-001");
  }

  @Test
  void baselineDiffSectionAbsentWhenNotSet() throws IOException {
    Path out = tempDir.resolve("summary.html");
    HtmlReporter.write(buildSummary(), out);

    String html = Files.readString(out);
    assertThat(html).doesNotContain("Baseline Regressions");
  }

  @Test
  void createsParentDirectories() throws IOException {
    Path out = tempDir.resolve("nested/dir/summary.html");
    HtmlReporter.write(buildSummary(), out);
    assertThat(out).exists();
  }

  @Test
  void escapeMethod_handlesSpecialChars() {
    assertThat(HtmlReporter.esc("<script>alert('xss')</script>"))
        .isEqualTo("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;");
    assertThat(HtmlReporter.esc(null)).isEmpty();
  }
}
