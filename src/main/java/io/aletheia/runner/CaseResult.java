package io.aletheia.runner;

import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.LlmResponse;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Result of running a single {@link EvalCase} through the runner. */
public final class CaseResult {

  private final EvalCase evalCase;
  private final LlmResponse response;
  private final List<EvalResult> evalResults;
  private final long totalLatencyMs;
  private final double estimatedCostUsd;
  private final boolean passed;

  public CaseResult(
      EvalCase evalCase,
      LlmResponse response,
      List<EvalResult> evalResults,
      long totalLatencyMs,
      double estimatedCostUsd) {
    this.evalCase = Objects.requireNonNull(evalCase, "evalCase must not be null");
    this.response = Objects.requireNonNull(response, "response must not be null");
    this.evalResults =
        Collections.unmodifiableList(
            Objects.requireNonNull(evalResults, "evalResults must not be null"));
    this.totalLatencyMs = totalLatencyMs;
    this.estimatedCostUsd = estimatedCostUsd;
    this.passed = evalResults.stream().allMatch(EvalResult::isPassed);
  }

  public EvalCase getEvalCase() {
    return evalCase;
  }

  public LlmResponse getResponse() {
    return response;
  }

  public List<EvalResult> getEvalResults() {
    return evalResults;
  }

  public long getTotalLatencyMs() {
    return totalLatencyMs;
  }

  public double getEstimatedCostUsd() {
    return estimatedCostUsd;
  }

  /** {@code true} when every evaluator in {@link #getEvalResults()} passed. */
  public boolean isPassed() {
    return passed;
  }

  @Override
  public String toString() {
    return "CaseResult{caseId='"
        + evalCase.getId()
        + "', passed="
        + passed
        + ", evaluators="
        + evalResults.size()
        + "}";
  }
}
