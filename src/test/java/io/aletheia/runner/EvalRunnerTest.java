package io.aletheia.runner;

import static org.assertj.core.api.Assertions.assertThat;

import io.aletheia.client.MockLlmClient;
import io.aletheia.model.EvalCase;
import io.aletheia.model.EvaluatorSpec;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvalRunnerTest {

  private static final MockLlmClient CLIENT =
      new MockLlmClient(Path.of("fixtures/mock-responses.yaml"));

  private static EvalCase makeCase(String id, String prompt, String mustInclude) {
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
  void runCase_passingCase_returnsPassed() {
    EvalCase evalCase = makeCase("runner-pass-001", "runner-pass", "contains-target");
    CaseResult result = EvalRunner.runCase(evalCase, CLIENT);

    assertThat(result.isPassed()).isTrue();
    assertThat(result.getEvalResults()).hasSize(1);
    assertThat(result.getEvalResults().get(0).isPassed()).isTrue();
    assertThat(result.getTotalLatencyMs()).isGreaterThanOrEqualTo(0);
  }

  @Test
  void runCase_failingCase_returnsNotPassed() {
    EvalCase evalCase = makeCase("runner-fail-001", "runner-fail", "contains-target");
    CaseResult result = EvalRunner.runCase(evalCase, CLIENT);

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getEvalResults().get(0).isPassed()).isFalse();
  }

  @Test
  void runCase_badEvaluatorSpec_surfacesAsFailingResult() {
    EvaluatorSpec badSpec = new EvaluatorSpec();
    badSpec.setType("unknown_evaluator_type_xyz");

    EvalCase evalCase = new EvalCase();
    evalCase.setId("bad-eval-001");
    evalCase.setPrompt("runner-pass");
    evalCase.setEvaluators(List.of(badSpec));

    CaseResult result = EvalRunner.runCase(evalCase, CLIENT);

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getEvalResults()).hasSize(1);
    assertThat(result.getEvalResults().get(0).getEvaluatorType())
        .isEqualTo("unknown_evaluator_type_xyz");
    assertThat(result.getEvalResults().get(0).getMessage())
        .contains("Evaluator construction error");
  }

  @Test
  void runSuite_aggregatesPassedAndFailed() {
    EvalCase pass1 = makeCase("runner-pass-001", "runner-pass", "contains-target");
    EvalCase fail1 = makeCase("runner-fail-001", "runner-fail", "contains-target");

    RunSummary summary = EvalRunner.runSuite(List.of(pass1, fail1), CLIENT);

    assertThat(summary.getTotalCases()).isEqualTo(2);
    assertThat(summary.getPassedCases()).isEqualTo(1);
    assertThat(summary.getFailedCases()).isEqualTo(1);
  }

  @Test
  void runSuite_evaluatorStats_computedCorrectly() {
    EvalCase pass1 = makeCase("runner-pass-001", "runner-pass", "contains-target");
    EvalCase pass2 = makeCase("runner-pass-002", "runner-pass", "contains-target");

    RunSummary summary = EvalRunner.runSuite(List.of(pass1, pass2), CLIENT);

    assertThat(summary.getEvaluatorStats()).containsKey("contains");
    RunSummary.EvaluatorStats stats = summary.getEvaluatorStats().get("contains");
    assertThat(stats.getCount()).isEqualTo(2);
    assertThat(stats.getPassed()).isEqualTo(2);
    assertThat(stats.getPassRate()).isEqualTo(1.0);
    assertThat(stats.getMeanScore()).isEqualTo(1.0);
  }

  @Test
  void runSuite_totalLatencyAndCost_aggregated() {
    EvalCase c1 = makeCase("runner-pass-001", "runner-pass", "contains-target");
    EvalCase c2 = makeCase("runner-fail-001", "runner-fail", "contains-target");

    RunSummary summary = EvalRunner.runSuite(List.of(c1, c2), CLIENT);

    assertThat(summary.getTotalLatencyMs()).isGreaterThanOrEqualTo(0);
    assertThat(summary.getTotalCostUsd()).isGreaterThanOrEqualTo(0.0);
  }
}
