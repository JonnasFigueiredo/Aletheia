package io.aletheia.runner.baseline;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * JSON-serialisable snapshot of a past run used for regression detection.
 *
 * <p>Shape: {@code { provider, model, generatedAt, cases: [{ caseId, evaluators: [{ type, score,
 * passed }] }] }}
 */
public final class BaselineSnapshot {

  @JsonProperty("provider")
  private String provider;

  @JsonProperty("model")
  private String model;

  @JsonProperty("generatedAt")
  private String generatedAt;

  @JsonProperty("cases")
  private List<CaseBaseline> cases;

  public BaselineSnapshot() {}

  public BaselineSnapshot(
      String provider, String model, String generatedAt, List<CaseBaseline> cases) {
    this.provider = provider;
    this.model = model;
    this.generatedAt = generatedAt;
    this.cases = cases == null ? List.of() : Collections.unmodifiableList(cases);
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getGeneratedAt() {
    return generatedAt;
  }

  public void setGeneratedAt(String generatedAt) {
    this.generatedAt = generatedAt;
  }

  public List<CaseBaseline> getCases() {
    return cases == null ? List.of() : cases;
  }

  public void setCases(List<CaseBaseline> cases) {
    this.cases = cases;
  }

  /** Per-case baseline entry. */
  public static final class CaseBaseline {

    @JsonProperty("caseId")
    private String caseId;

    @JsonProperty("evaluators")
    private List<EvaluatorBaseline> evaluators;

    public CaseBaseline() {}

    public CaseBaseline(String caseId, List<EvaluatorBaseline> evaluators) {
      this.caseId = caseId;
      this.evaluators = evaluators == null ? List.of() : Collections.unmodifiableList(evaluators);
    }

    public String getCaseId() {
      return caseId;
    }

    public void setCaseId(String caseId) {
      this.caseId = caseId;
    }

    public List<EvaluatorBaseline> getEvaluators() {
      return evaluators == null ? List.of() : evaluators;
    }

    public void setEvaluators(List<EvaluatorBaseline> evaluators) {
      this.evaluators = evaluators;
    }
  }

  /** Per-evaluator baseline entry. */
  public static final class EvaluatorBaseline {

    @JsonProperty("type")
    private String type;

    @JsonProperty("score")
    private double score;

    @JsonProperty("passed")
    private boolean passed;

    public EvaluatorBaseline() {}

    public EvaluatorBaseline(String type, double score, boolean passed) {
      this.type = type;
      this.score = score;
      this.passed = passed;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public double getScore() {
      return score;
    }

    public void setScore(double score) {
      this.score = score;
    }

    public boolean isPassed() {
      return passed;
    }

    public void setPassed(boolean passed) {
      this.passed = passed;
    }
  }
}
