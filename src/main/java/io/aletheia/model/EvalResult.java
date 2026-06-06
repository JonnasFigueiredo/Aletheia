package io.aletheia.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** Outcome of a single evaluator applied to an LLM response. */
public final class EvalResult {

  private final String evaluatorType;
  private final boolean passed;
  private final double score;
  private final String message;
  private final Map<String, Object> details;

  private EvalResult(
      String evaluatorType,
      boolean passed,
      double score,
      String message,
      Map<String, Object> details) {
    this.evaluatorType = Objects.requireNonNull(evaluatorType, "evaluatorType must not be null");
    this.passed = passed;
    this.score = score;
    this.message = message;
    this.details = details == null ? Collections.emptyMap() : Collections.unmodifiableMap(details);
  }

  public static EvalResult pass(String evaluatorType) {
    return new EvalResult(evaluatorType, true, 1.0, null, null);
  }

  public static EvalResult fail(String evaluatorType, String message) {
    return new EvalResult(evaluatorType, false, 0.0, message, null);
  }

  public static EvalResult of(
      String evaluatorType, double score, double threshold, String message) {
    return new EvalResult(evaluatorType, score >= threshold, score, message, null);
  }

  public static EvalResult of(
      String evaluatorType,
      boolean passed,
      double score,
      String message,
      Map<String, Object> details) {
    return new EvalResult(evaluatorType, passed, score, message, details);
  }

  public String getEvaluatorType() {
    return evaluatorType;
  }

  public boolean isPassed() {
    return passed;
  }

  /** Score in range [0.0, 1.0]. */
  public double getScore() {
    return score;
  }

  public String getMessage() {
    return message;
  }

  public Map<String, Object> getDetails() {
    return details;
  }

  @Override
  public String toString() {
    return "EvalResult{evaluatorType='"
        + evaluatorType
        + "', passed="
        + passed
        + ", score="
        + score
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EvalResult)) return false;
    EvalResult that = (EvalResult) o;
    return passed == that.passed
        && Double.compare(score, that.score) == 0
        && Objects.equals(evaluatorType, that.evaluatorType)
        && Objects.equals(message, that.message)
        && Objects.equals(details, that.details);
  }

  @Override
  public int hashCode() {
    return Objects.hash(evaluatorType, passed, score, message, details);
  }
}
