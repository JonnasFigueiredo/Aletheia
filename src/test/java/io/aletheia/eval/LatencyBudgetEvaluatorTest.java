package io.aletheia.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.model.LlmResponse;
import org.junit.jupiter.api.Test;

class LatencyBudgetEvaluatorTest {

  private static EvaluatorSpec spec(long maxMs) {
    EvaluatorSpec s = new EvaluatorSpec();
    s.setType("latency_budget");
    s.addParam("max_latency_ms", maxMs);
    return s;
  }

  private static LlmResponse resp(long latencyMs) {
    return new LlmResponse("ok", 5, 10, latencyMs);
  }

  private static final EvalCase EC = new EvalCase();

  @Test
  void exactlyAtBudget_passes_withFullScore() {
    Evaluator ev = new LatencyBudgetEvaluator(spec(1000));

    EvalResult result = ev.evaluate(EC, resp(1000));

    assertThat(result.isPassed()).isTrue();
    assertThat(result.getScore()).isEqualTo(1.0);
  }

  @Test
  void belowBudget_passes_withFullScore() {
    Evaluator ev = new LatencyBudgetEvaluator(spec(1000));

    EvalResult result = ev.evaluate(EC, resp(500));

    assertThat(result.isPassed()).isTrue();
    assertThat(result.getScore()).isEqualTo(1.0);
  }

  @Test
  void halfOverBudget_partialCreditScore() {
    Evaluator ev = new LatencyBudgetEvaluator(spec(1000));

    EvalResult result = ev.evaluate(EC, resp(1500));

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getScore()).isCloseTo(0.5, offset(1e-9));
  }

  @Test
  void doubleTheBudget_zeroScore() {
    Evaluator ev = new LatencyBudgetEvaluator(spec(1000));

    EvalResult result = ev.evaluate(EC, resp(2000));

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getScore()).isEqualTo(0.0);
  }

  @Test
  void farOverBudget_scoreClampedAtZero() {
    Evaluator ev = new LatencyBudgetEvaluator(spec(100));

    EvalResult result = ev.evaluate(EC, resp(10000));

    assertThat(result.getScore()).isEqualTo(0.0);
    assertThat(result.isPassed()).isFalse();
  }
}
