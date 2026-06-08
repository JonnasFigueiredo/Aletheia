package io.aletheia.eval;

import io.aletheia.client.LlmClient;
import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.model.LlmResponse;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compares the response to {@link EvalCase#getGolden()} via embedding cosine similarity, falling
 * back to bag-of-words cosine when embeddings are unavailable.
 */
final class SemanticSimilarityEvaluator implements Evaluator {

  private static final String TYPE = "semantic_similarity";
  private static final double DEFAULT_THRESHOLD = 0.75;

  private final double threshold;
  private final LlmClient llmClient;

  SemanticSimilarityEvaluator(EvaluatorSpec spec, LlmClient llmClient) {
    this.llmClient = llmClient;
    Object t = spec.getParams().get("threshold");
    this.threshold = t instanceof Number n ? n.doubleValue() : DEFAULT_THRESHOLD;
  }

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public EvalResult evaluate(EvalCase evalCase, LlmResponse response) {
    String golden = evalCase.getGolden();
    if (golden == null || golden.isBlank()) {
      Map<String, Object> details = new HashMap<>();
      details.put("error", "No golden reference defined on EvalCase");
      return EvalResult.of(TYPE, false, 0.0, "No golden reference defined", details);
    }

    double similarity;
    String mode;

    Optional<double[]> respEmbed =
        llmClient != null ? llmClient.embed(response.getText()) : Optional.empty();
    Optional<double[]> goldenEmbed = llmClient != null ? llmClient.embed(golden) : Optional.empty();

    if (respEmbed.isPresent() && goldenEmbed.isPresent()) {
      similarity = cosineSimilarity(respEmbed.get(), goldenEmbed.get());
      mode = "embedding";
    } else {
      similarity = bagOfWordsCosine(response.getText(), golden);
      mode = "bag_of_words";
    }

    boolean passed = similarity >= threshold;
    Map<String, Object> details = new HashMap<>();
    details.put("similarity", similarity);
    details.put("threshold", threshold);
    details.put("mode", mode);

    return EvalResult.of(
        TYPE,
        passed,
        similarity,
        passed ? "Similarity above threshold" : "Similarity below threshold",
        details);
  }

  private static double cosineSimilarity(double[] a, double[] b) {
    int len = Math.min(a.length, b.length);
    double dot = 0.0, normA = 0.0, normB = 0.0;
    for (int i = 0; i < len; i++) {
      dot += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }
    if (normA == 0.0 || normB == 0.0) return 0.0;
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
  }

  static double bagOfWordsCosine(String a, String b) {
    Set<String> aTokens = tokenize(a);
    Set<String> bTokens = tokenize(b);
    if (aTokens.isEmpty() || bTokens.isEmpty()) return 0.0;
    long intersection = aTokens.stream().filter(bTokens::contains).count();
    return (double) intersection / (Math.sqrt(aTokens.size()) * Math.sqrt(bTokens.size()));
  }

  static Set<String> tokenize(String text) {
    return Arrays.stream(text.toLowerCase().split("[^a-zA-Z0-9]+"))
        .filter(t -> t.length() >= 2)
        .collect(Collectors.toSet());
  }
}
