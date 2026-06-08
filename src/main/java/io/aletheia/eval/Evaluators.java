package io.aletheia.eval;

import io.aletheia.client.LlmClient;
import io.aletheia.config.CostTable;
import io.aletheia.model.EvaluatorSpec;

/** Factory that maps an {@link EvaluatorSpec} to the correct {@link Evaluator} implementation. */
public final class Evaluators {

  private Evaluators() {}

  /**
   * Returns the {@link Evaluator} configured by {@code spec}.
   *
   * @param spec the evaluator specification (type + params)
   * @param llmClient used only by judge-mode evaluators; may be {@code null} to force heuristic
   *     fallback
   * @param costTable used only by {@code cost_budget}; may be {@code null} to load defaults
   * @throws IllegalArgumentException for unknown evaluator types
   */
  public static Evaluator forSpec(EvaluatorSpec spec, LlmClient llmClient, CostTable costTable) {
    return switch (spec.getType()) {
      case "json_schema" -> new JsonSchemaEvaluator(spec);
      case "contains" -> new ContainsEvaluator(spec);
      case "regex" -> new RegexEvaluator(spec);
      case "semantic_similarity" -> new SemanticSimilarityEvaluator(spec, llmClient);
      case "latency_budget" -> new LatencyBudgetEvaluator(spec);
      case "cost_budget" -> new CostBudgetEvaluator(spec, costTable);
      case "faithfulness" -> new FaithfulnessEvaluator(spec, llmClient);
      case "rubric_judge" -> new RubricJudgeEvaluator(spec, llmClient);
      case "safety" -> new SafetyEvaluator(spec, llmClient);
      default -> throw new IllegalArgumentException("Unknown evaluator type: " + spec.getType());
    };
  }
}
