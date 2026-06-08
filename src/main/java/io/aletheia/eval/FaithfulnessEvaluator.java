package io.aletheia.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aletheia.client.LlmClient;
import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.model.LlmRequest;
import io.aletheia.model.LlmResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Verifies that the response is supported by {@link EvalCase#getContext()}.
 *
 * <p>Judge mode: calls the LLM with a structured prompt expecting {@code {"supported": bool,
 * "score": 0..1, "reasons": [...]}}. Falls back to token-overlap heuristic when the client is
 * {@code mock}, the client is {@code null}, or the judge returns malformed JSON.
 */
final class FaithfulnessEvaluator implements Evaluator {

  private static final String TYPE = "faithfulness";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String JUDGE_PROMPT =
      "You are a faithfulness judge.\n\nContext:\n%s\n\nAnswer:\n%s\n\n"
          + "Is the answer fully supported by the context? "
          + "Respond with ONLY valid JSON: "
          + "{\"supported\": true|false, \"score\": 0.0-1.0, \"reasons\": [...]}";

  private static final List<String> CONTRADICTION_MARKERS =
      List.of(
          "that is incorrect",
          "that is wrong",
          "not supported",
          "contradicts",
          "contrary to",
          "goes against");

  private final String judgeModel;
  private final LlmClient llmClient;

  FaithfulnessEvaluator(EvaluatorSpec spec, LlmClient llmClient) {
    this.llmClient = llmClient;
    Object m = spec.getParams().get("judge_model");
    this.judgeModel = m != null ? m.toString() : null;
  }

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public EvalResult evaluate(EvalCase evalCase, LlmResponse response) {
    String context = evalCase.getContext();
    if (context == null || context.isBlank()) {
      return heuristic("", response.getText(), "No context provided");
    }

    if (useHeuristic()) {
      return heuristic(context, response.getText(), null);
    }

    String prompt = String.format(JUDGE_PROMPT, context, response.getText());
    LlmRequest.Builder reqBuilder = LlmRequest.builder(prompt).temperature(0.0);
    if (judgeModel != null) {
      reqBuilder.model(judgeModel);
    }

    try {
      LlmResponse judgeResp = llmClient.complete(reqBuilder.build());
      JsonNode root = MAPPER.readTree(judgeResp.getText());

      boolean supported = root.path("supported").asBoolean(false);
      double score = root.path("score").asDouble(supported ? 1.0 : 0.0);
      List<String> reasons = new ArrayList<>();
      JsonNode reasonsNode = root.path("reasons");
      if (reasonsNode.isArray()) {
        reasonsNode.forEach(n -> reasons.add(n.asText()));
      }

      Map<String, Object> details = new HashMap<>();
      details.put("judge_prompt", prompt);
      details.put("judge_response", judgeResp.getText());
      details.put("reasons", reasons);

      return EvalResult.of(
          TYPE,
          supported,
          score,
          supported ? "Answer is supported by context" : "Answer not fully supported",
          details);
    } catch (Exception e) {
      return heuristic(context, response.getText(), null);
    }
  }

  private boolean useHeuristic() {
    return llmClient == null || "mock".equals(llmClient.providerId());
  }

  private EvalResult heuristic(String context, String responseText, String overrideMessage) {
    Set<String> contextTokens = tokenize(context);
    Set<String> responseTokens = tokenize(responseText);

    double score;
    if (responseTokens.isEmpty()) {
      score = 0.0;
    } else {
      long overlap = responseTokens.stream().filter(contextTokens::contains).count();
      score = (double) overlap / responseTokens.size();
    }

    String lower = responseText.toLowerCase();
    boolean hasContradiction = CONTRADICTION_MARKERS.stream().anyMatch(lower::contains);

    boolean passed = score >= 0.5 && !hasContradiction;

    Map<String, Object> details = new HashMap<>();
    details.put("mode", "heuristic");
    details.put("token_overlap_score", score);
    details.put("has_contradiction_marker", hasContradiction);
    if (overrideMessage != null) {
      details.put("reason", overrideMessage);
    }

    return EvalResult.of(
        TYPE,
        passed,
        score,
        passed ? "Heuristic faithfulness passed" : "Heuristic faithfulness failed",
        details);
  }

  private static Set<String> tokenize(String text) {
    return Arrays.stream(text.toLowerCase().split("[^a-zA-Z0-9]+"))
        .filter(t -> t.length() >= 2)
        .collect(Collectors.toSet());
  }
}
