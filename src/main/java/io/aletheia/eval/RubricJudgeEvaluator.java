package io.aletheia.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aletheia.client.LlmClient;
import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.model.LlmRequest;
import io.aletheia.model.LlmResponse;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Scores the response against a rubric of named criteria using an LLM judge, each scored 0–5.
 * Overall score = mean / 5. Falls back to a length-based proxy (capped at 0.6) for mock clients.
 */
final class RubricJudgeEvaluator implements Evaluator {

  private static final String TYPE = "rubric_judge";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final double DEFAULT_THRESHOLD = 0.6;

  private static final Map<String, String> DEFAULT_RUBRIC =
      Map.of(
          "clarity", "Is the response clear and easy to understand?",
          "correctness", "Is the response factually correct and accurate?",
          "completeness", "Does the response fully address the question?");

  private final Map<String, String> rubric;
  private final double threshold;
  private final LlmClient llmClient;

  @SuppressWarnings("unchecked")
  RubricJudgeEvaluator(EvaluatorSpec spec, LlmClient llmClient) {
    this.llmClient = llmClient;
    Map<String, Object> p = spec.getParams();

    Object rawRubric = p.get("rubric");
    if (rawRubric instanceof Map<?, ?> m) {
      Map<String, String> r = new LinkedHashMap<>();
      m.forEach((k, v) -> r.put(k.toString(), v != null ? v.toString() : ""));
      this.rubric = Map.copyOf(r);
    } else {
      this.rubric = DEFAULT_RUBRIC;
    }

    Object t = p.get("threshold");
    this.threshold = t instanceof Number n ? n.doubleValue() : DEFAULT_THRESHOLD;
  }

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public EvalResult evaluate(EvalCase evalCase, LlmResponse response) {
    if (useHeuristic()) {
      return heuristic(response.getText());
    }

    String prompt = buildPrompt(response.getText());
    LlmRequest req = LlmRequest.builder(prompt).temperature(0.0).build();

    try {
      LlmResponse judgeResp = llmClient.complete(req);
      JsonNode root = MAPPER.readTree(judgeResp.getText());

      double total = 0.0;
      int count = 0;
      Map<String, Object> perCriterion = new HashMap<>();

      for (String criterion : rubric.keySet()) {
        JsonNode entry = root.path(criterion);
        if (!entry.isMissingNode()) {
          int rawScore = Math.min(5, Math.max(0, entry.path("score").asInt(0)));
          String rationale = entry.path("rationale").asText("");
          total += rawScore;
          count++;
          Map<String, Object> crit = new HashMap<>();
          crit.put("score", rawScore);
          crit.put("rationale", rationale);
          perCriterion.put(criterion, crit);
        }
      }

      double score = count > 0 ? (total / count) / 5.0 : 0.0;
      boolean passed = score >= threshold;

      Map<String, Object> details = new HashMap<>();
      details.put("judge_prompt", prompt);
      details.put("judge_response", judgeResp.getText());
      details.put("criteria", perCriterion);

      return EvalResult.of(
          TYPE,
          passed,
          score,
          passed ? "Rubric score above threshold" : "Rubric score below threshold",
          details);
    } catch (Exception e) {
      return heuristic(response.getText());
    }
  }

  private boolean useHeuristic() {
    return llmClient == null || "mock".equals(llmClient.providerId());
  }

  private EvalResult heuristic(String responseText) {
    double score = Math.min(0.6, responseText.trim().length() / 500.0);
    boolean passed = score >= threshold;
    Map<String, Object> details = new HashMap<>();
    details.put("mode", "heuristic_length_proxy");
    details.put("response_length", responseText.trim().length());
    return EvalResult.of(
        TYPE,
        passed,
        score,
        passed ? "Rubric heuristic passed" : "Rubric heuristic failed",
        details);
  }

  private String buildPrompt(String responseText) {
    StringBuilder sb = new StringBuilder();
    sb.append(
        "You are an evaluation judge. Score the following response on each criterion from 0"
            + " to 5 (0 = worst, 5 = best).\n\nCriteria:\n");
    rubric.forEach(
        (name, desc) -> sb.append("- ").append(name).append(": ").append(desc).append('\n'));
    sb.append("\nResponse to evaluate:\n").append(responseText);
    sb.append("\n\nRespond with ONLY valid JSON. Example format:\n{");
    rubric
        .keySet()
        .forEach(
            name ->
                sb.append("\n  \"")
                    .append(name)
                    .append("\": {\"score\": <0-5>, \"rationale\": \"<brief rationale>\"},"));
    if (sb.charAt(sb.length() - 1) == ',') {
      sb.deleteCharAt(sb.length() - 1);
    }
    sb.append("\n}");
    return sb.toString();
  }
}
