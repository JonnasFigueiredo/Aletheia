package io.aletheia.eval;

import io.aletheia.config.CostTable;
import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.model.LlmResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Asserts that the computed USD cost of the response stays within a budget. Overruns receive
 * partial credit using the same formula as {@link LatencyBudgetEvaluator}.
 *
 * <p>Cost is computed via {@link CostTable} using {@link EvalCase#getModel()}.
 */
final class CostBudgetEvaluator implements Evaluator {

  private static final String TYPE = "cost_budget";

  private final double maxCostUsd;
  private final CostTable costTable;

  @SuppressWarnings("unchecked")
  CostBudgetEvaluator(EvaluatorSpec spec, CostTable costTable) {
    Map<String, Object> p = spec.getParams();
    Object raw = p.get("max_cost_usd");
    if (raw == null) {
      throw new IllegalArgumentException("CostBudgetEvaluator requires a 'max_cost_usd' parameter");
    }
    this.maxCostUsd = ((Number) raw).doubleValue();

    CostTable base = costTable != null ? costTable : CostTable.fromResource();
    Object overrideRaw = p.get("cost_table");
    if (overrideRaw instanceof Map<?, ?> m) {
      this.costTable = base.withOverrides((Map<String, Object>) m);
    } else {
      this.costTable = base;
    }
  }

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public EvalResult evaluate(EvalCase evalCase, LlmResponse response) {
    double cost =
        costTable.computeCost(evalCase.getModel(), response.getTokensIn(), response.getTokensOut());
    boolean passed = cost <= maxCostUsd;

    double score;
    if (maxCostUsd == 0.0) {
      score = passed ? 1.0 : 0.0;
    } else {
      double raw = 1.0 - (cost - maxCostUsd) / maxCostUsd;
      score = Math.min(1.0, Math.max(0.0, raw));
    }

    Map<String, Object> details = new HashMap<>();
    details.put("cost_usd", cost);
    details.put("max_cost_usd", maxCostUsd);
    details.put("tokens_in", response.getTokensIn());
    details.put("tokens_out", response.getTokensOut());
    details.put("model", evalCase.getModel());

    return EvalResult.of(
        TYPE, passed, score, passed ? "Cost within budget" : "Cost exceeded budget", details);
  }
}
