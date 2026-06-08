package io.aletheia.runner;

import io.aletheia.model.EvalResult;
import io.aletheia.runner.baseline.Regression;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Aggregated outcome of running a suite of eval cases. */
public final class RunSummary {

  private final String provider;
  private final String model;
  private final List<CaseResult> caseResults;
  private final int totalCases;
  private final int passedCases;
  private final int failedCases;
  private final long totalLatencyMs;
  private final double totalCostUsd;
  private final Map<String, EvaluatorStats> evaluatorStats;
  private List<Regression> baselineDiff;

  private RunSummary(
      String provider,
      String model,
      List<CaseResult> caseResults,
      long totalLatencyMs,
      double totalCostUsd,
      Map<String, EvaluatorStats> evaluatorStats) {
    this.provider = provider;
    this.model = model;
    this.caseResults = Collections.unmodifiableList(Objects.requireNonNull(caseResults));
    this.totalCases = caseResults.size();
    this.passedCases = (int) caseResults.stream().filter(CaseResult::isPassed).count();
    this.failedCases = this.totalCases - this.passedCases;
    this.totalLatencyMs = totalLatencyMs;
    this.totalCostUsd = totalCostUsd;
    this.evaluatorStats = Collections.unmodifiableMap(Objects.requireNonNull(evaluatorStats));
  }

  /** Builds a {@link RunSummary} from a list of already-executed case results. */
  public static RunSummary of(String provider, String model, List<CaseResult> results) {
    long totalLatency = results.stream().mapToLong(CaseResult::getTotalLatencyMs).sum();
    double totalCost = results.stream().mapToDouble(CaseResult::getEstimatedCostUsd).sum();

    Map<String, List<Double>> scoresByType = new LinkedHashMap<>();
    Map<String, Integer> passedByType = new LinkedHashMap<>();
    for (CaseResult r : results) {
      for (EvalResult er : r.getEvalResults()) {
        scoresByType
            .computeIfAbsent(er.getEvaluatorType(), k -> new ArrayList<>())
            .add(er.getScore());
        if (er.isPassed()) {
          passedByType.merge(er.getEvaluatorType(), 1, Integer::sum);
        }
      }
    }

    Map<String, EvaluatorStats> stats = new LinkedHashMap<>();
    scoresByType.forEach(
        (type, scores) -> {
          int count = scores.size();
          int passed = passedByType.getOrDefault(type, 0);
          double meanScore = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
          double passRate = count == 0 ? 0.0 : (double) passed / count;
          stats.put(type, new EvaluatorStats(count, passed, meanScore, passRate));
        });

    return new RunSummary(
        provider, model, new ArrayList<>(results), totalLatency, totalCost, stats);
  }

  public String getProvider() {
    return provider;
  }

  public String getModel() {
    return model;
  }

  public List<CaseResult> getCaseResults() {
    return caseResults;
  }

  public int getTotalCases() {
    return totalCases;
  }

  public int getPassedCases() {
    return passedCases;
  }

  public int getFailedCases() {
    return failedCases;
  }

  public long getTotalLatencyMs() {
    return totalLatencyMs;
  }

  public double getTotalCostUsd() {
    return totalCostUsd;
  }

  public Map<String, EvaluatorStats> getEvaluatorStats() {
    return evaluatorStats;
  }

  /** Optional baseline diff populated by {@code BaselineComparer}; may be {@code null}. */
  public List<Regression> getBaselineDiff() {
    return baselineDiff;
  }

  public void setBaselineDiff(List<Regression> baselineDiff) {
    this.baselineDiff = baselineDiff == null ? null : Collections.unmodifiableList(baselineDiff);
  }

  /** Per-evaluator-type aggregate statistics. */
  public static final class EvaluatorStats {

    private final int count;
    private final int passed;
    private final double meanScore;
    private final double passRate;

    public EvaluatorStats(int count, int passed, double meanScore, double passRate) {
      this.count = count;
      this.passed = passed;
      this.meanScore = meanScore;
      this.passRate = passRate;
    }

    public int getCount() {
      return count;
    }

    public int getPassed() {
      return passed;
    }

    public double getMeanScore() {
      return meanScore;
    }

    public double getPassRate() {
      return passRate;
    }
  }
}
