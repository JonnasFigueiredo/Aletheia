package io.aletheia.runner.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import io.aletheia.client.MockLlmClient;
import io.aletheia.model.EvalCase;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.runner.EvalRunner;
import io.aletheia.runner.RunSummary;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class BaselineComparerTest {

  private static final MockLlmClient CLIENT =
      new MockLlmClient(Path.of("fixtures/mock-responses.yaml"));

  private static BaselineSnapshot snapshotWith(
      String caseId, String evalType, double score, boolean passed) {
    BaselineSnapshot.EvaluatorBaseline eb =
        new BaselineSnapshot.EvaluatorBaseline(evalType, score, passed);
    BaselineSnapshot.CaseBaseline cb = new BaselineSnapshot.CaseBaseline(caseId, List.of(eb));
    return new BaselineSnapshot("mock", "mock-model", "2025-01-01T00:00:00Z", List.of(cb));
  }

  private static EvalCase makeContainsCase(String id, String prompt, String mustInclude) {
    EvaluatorSpec spec = new EvaluatorSpec();
    spec.setType("contains");
    spec.addParam("must_include", List.of(mustInclude));
    EvalCase c = new EvalCase();
    c.setId(id);
    c.setPrompt(prompt);
    c.setEvaluators(List.of(spec));
    return c;
  }

  @Test
  void noRegressionWhenCurrentScoreEqualToBaseline() {
    EvalCase c = makeContainsCase("runner-pass-001", "runner-pass", "contains-target");
    RunSummary summary = EvalRunner.runSuite(List.of(c), CLIENT);

    BaselineSnapshot baseline = snapshotWith("runner-pass-001", "contains", 1.0, true);
    List<Regression> regressions = BaselineComparer.compare(summary, baseline, 0.05);

    assertThat(regressions).isEmpty();
  }

  @Test
  void regressionDetectedWhenScoreDropsBeyondTolerance() {
    EvalCase c = makeContainsCase("runner-fail-001", "runner-fail", "contains-target");
    RunSummary summary = EvalRunner.runSuite(List.of(c), CLIENT);

    BaselineSnapshot baseline = snapshotWith("runner-fail-001", "contains", 1.0, true);
    List<Regression> regressions = BaselineComparer.compare(summary, baseline, 0.05);

    assertThat(regressions).hasSize(1);
    Regression r = regressions.get(0);
    assertThat(r.getCaseId()).isEqualTo("runner-fail-001");
    assertThat(r.getEvaluatorType()).isEqualTo("contains");
    assertThat(r.getPreviousScore()).isEqualTo(1.0);
    assertThat(r.getCurrentScore()).isEqualTo(0.0);
    assertThat(r.getDelta()).isLessThan(0);
  }

  @Test
  void toleranceBoundary_noRegressionWithinTolerance() {
    EvalCase c = makeContainsCase("runner-fail-001", "runner-fail", "contains-target");
    RunSummary summary = EvalRunner.runSuite(List.of(c), CLIENT);

    BaselineSnapshot baseline = snapshotWith("runner-fail-001", "contains", 0.04, false);
    List<Regression> regressions = BaselineComparer.compare(summary, baseline, 0.05);

    assertThat(regressions).isEmpty();
  }

  @Test
  void missingCaseInBaseline_skipped() {
    EvalCase c = makeContainsCase("runner-pass-001", "runner-pass", "contains-target");
    RunSummary summary = EvalRunner.runSuite(List.of(c), CLIENT);

    BaselineSnapshot emptyBaseline =
        new BaselineSnapshot("mock", "model", "2025-01-01T00:00:00Z", List.of());
    List<Regression> regressions = BaselineComparer.compare(summary, emptyBaseline, 0.05);

    assertThat(regressions).isEmpty();
  }

  @Test
  void missingEvaluatorInBaseline_skipped() {
    EvalCase c = makeContainsCase("runner-pass-001", "runner-pass", "contains-target");
    RunSummary summary = EvalRunner.runSuite(List.of(c), CLIENT);

    BaselineSnapshot baseline = snapshotWith("runner-pass-001", "json_schema", 1.0, true);
    List<Regression> regressions = BaselineComparer.compare(summary, baseline, 0.05);

    assertThat(regressions).isEmpty();
  }
}
