package io.aletheia.runner.baseline;

import java.util.Objects;

/** A detected score regression vs. a stored baseline. */
public final class Regression {

  private final String caseId;
  private final String evaluatorType;
  private final double previousScore;
  private final double currentScore;
  private final double delta;

  public Regression(
      String caseId, String evaluatorType, double previousScore, double currentScore) {
    this.caseId = Objects.requireNonNull(caseId, "caseId");
    this.evaluatorType = Objects.requireNonNull(evaluatorType, "evaluatorType");
    this.previousScore = previousScore;
    this.currentScore = currentScore;
    this.delta = currentScore - previousScore;
  }

  public String getCaseId() {
    return caseId;
  }

  public String getEvaluatorType() {
    return evaluatorType;
  }

  public double getPreviousScore() {
    return previousScore;
  }

  public double getCurrentScore() {
    return currentScore;
  }

  /** Negative value means the current score dropped vs. baseline. */
  public double getDelta() {
    return delta;
  }

  @Override
  public String toString() {
    return "Regression{caseId='"
        + caseId
        + "', evaluatorType='"
        + evaluatorType
        + "', previousScore="
        + previousScore
        + ", currentScore="
        + currentScore
        + ", delta="
        + delta
        + "}";
  }
}
