package io.aletheia.eval;

import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.model.LlmResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Asserts that response latency stays within a budget. Overruns receive partial credit so small
 * regressions are visible in scoring without immediately failing.
 *
 * <p>Score formula: {@code clamp(1 − (latency − budget) / budget, 0, 1)}.
 */
final class LatencyBudgetEvaluator implements Evaluator {

  private static final String TYPE = "latency_budget";

  private final long maxLatencyMs;

  LatencyBudgetEvaluator(EvaluatorSpec spec) {
    Object raw = spec.getParams().get("max_latency_ms");
    if (raw == null) {
      throw new IllegalArgumentException(
          "LatencyBudgetEvaluator requires a 'max_latency_ms' parameter");
    }
    this.maxLatencyMs = ((Number) raw).longValue();
  }

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public EvalResult evaluate(EvalCase evalCase, LlmResponse response) {
    long latency = response.getLatencyMs();
    boolean passed = latency <= maxLatencyMs;

    double score;
    if (maxLatencyMs == 0) {
      score = passed ? 1.0 : 0.0;
    } else {
      double raw = 1.0 - (double) (latency - maxLatencyMs) / maxLatencyMs;
      score = Math.min(1.0, Math.max(0.0, raw));
    }

    Map<String, Object> details = new HashMap<>();
    details.put("latency_ms", latency);
    details.put("max_latency_ms", maxLatencyMs);

    return EvalResult.of(
        TYPE, passed, score, passed ? "Latency within budget" : "Latency exceeded budget", details);
  }
}
