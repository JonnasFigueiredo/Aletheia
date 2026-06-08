package io.aletheia.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import io.aletheia.config.CostTable;
import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.model.LlmResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CostBudgetEvaluatorTest {

  private static EvaluatorSpec spec(double maxCost) {
    EvaluatorSpec s = new EvaluatorSpec();
    s.setType("cost_budget");
    s.addParam("max_cost_usd", maxCost);
    return s;
  }

  private static EvalCase caseForModel(String model) {
    EvalCase ec = new EvalCase();
    ec.setId("test");
    ec.setModel(model);
    return ec;
  }

  /** Response with 1 000 input tokens and 1 000 output tokens. */
  private static LlmResponse resp1k() {
    return new LlmResponse("ok", 1000, 1000, 100);
  }

  @Test
  void mockModelHasZeroCost_alwaysPasses() {
    Evaluator ev = new CostBudgetEvaluator(spec(0.0), CostTable.fromResource());

    EvalResult result = ev.evaluate(caseForModel("mock"), resp1k());

    assertThat(result.isPassed()).isTrue();
    assertThat(result.getScore()).isEqualTo(1.0);
    assertThat((Double) result.getDetails().get("cost_usd")).isEqualTo(0.0);
  }

  @Test
  void exactlyAtBudget_passes_withFullScore() {
    // gpt-4o-mini: 0.00015/1k in + 0.0006/1k out = 0.00075 for 1k/1k
    CostTable ct = CostTable.fromResource();
    Evaluator ev = new CostBudgetEvaluator(spec(0.00075), ct);

    EvalResult result = ev.evaluate(caseForModel("gpt-4o-mini"), resp1k());

    assertThat(result.isPassed()).isTrue();
    assertThat(result.getScore()).isEqualTo(1.0);
  }

  @Test
  void doubleTheBudget_zeroScore() {
    // budget = 0.00075, cost = 0.00075 → budget = 0.000375 makes cost 2x budget
    CostTable ct = CostTable.fromResource();
    Evaluator ev = new CostBudgetEvaluator(spec(0.000375), ct);

    EvalResult result = ev.evaluate(caseForModel("gpt-4o-mini"), resp1k());

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getScore()).isCloseTo(0.0, offset(1e-9));
  }

  @Test
  void partialOverrun_partialCreditScore() {
    // gpt-4o-mini cost for 1k/1k = 0.00075; set budget to 0.0005 → overrun by 0.00025
    // score = 1 - (0.00075 - 0.0005) / 0.0005 = 1 - 0.5 = 0.5
    CostTable ct = CostTable.fromResource();
    Evaluator ev = new CostBudgetEvaluator(spec(0.0005), ct);

    EvalResult result = ev.evaluate(caseForModel("gpt-4o-mini"), resp1k());

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getScore()).isCloseTo(0.5, offset(1e-6));
  }

  @Test
  void inlineCostTableOverride_takesEffect() {
    Map<String, Object> override =
        Map.of("test-model", Map.of("input_per_1k", 0.001, "output_per_1k", 0.002));
    EvaluatorSpec s = new EvaluatorSpec();
    s.setType("cost_budget");
    s.addParam("max_cost_usd", 0.01);
    s.addParam("cost_table", override);

    Evaluator ev = new CostBudgetEvaluator(s, CostTable.fromResource());

    // 1k in * 0.001/1k + 1k out * 0.002/1k = 0.003 USD
    EvalResult result = ev.evaluate(caseForModel("test-model"), resp1k());

    assertThat(result.isPassed()).isTrue();
    assertThat((Double) result.getDetails().get("cost_usd")).isCloseTo(0.003, offset(1e-9));
  }
}
