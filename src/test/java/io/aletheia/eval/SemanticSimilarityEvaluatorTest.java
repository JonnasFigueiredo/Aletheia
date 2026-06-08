package io.aletheia.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.model.LlmResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SemanticSimilarityEvaluatorTest {

  private static EvaluatorSpec spec(Map<String, Object> params) {
    EvaluatorSpec s = new EvaluatorSpec();
    s.setType("semantic_similarity");
    params.forEach(s::addParam);
    return s;
  }

  private static EvalCase caseWithGolden(String golden) {
    EvalCase ec = new EvalCase();
    ec.setId("test");
    ec.setGolden(golden);
    return ec;
  }

  private static LlmResponse resp(String text) {
    return new LlmResponse(text, 5, 10, 50);
  }

  @Test
  void bagOfWords_identicalStrings_scoreIsOne() {
    Evaluator ev = new SemanticSimilarityEvaluator(spec(Map.of("threshold", 0.75)), null);
    String text = "the quick brown fox jumps over the lazy dog";

    EvalResult result = ev.evaluate(caseWithGolden(text), resp(text));

    assertThat(result.getScore()).isCloseTo(1.0, offset(1e-9));
    assertThat(result.isPassed()).isTrue();
    assertThat(result.getDetails().get("mode")).isEqualTo("bag_of_words");
  }

  @Test
  void bagOfWords_noOverlap_scoreIsZero() {
    Evaluator ev = new SemanticSimilarityEvaluator(spec(Map.of("threshold", 0.5)), null);

    EvalResult result =
        ev.evaluate(caseWithGolden("apple banana cherry"), resp("dog elephant fish"));

    assertThat(result.getScore()).isEqualTo(0.0);
    assertThat(result.isPassed()).isFalse();
  }

  @Test
  void thresholdGating_belowThreshold_fails() {
    Evaluator ev = new SemanticSimilarityEvaluator(spec(Map.of("threshold", 0.9)), null);

    EvalResult result =
        ev.evaluate(caseWithGolden("java is great"), resp("java is sometimes okay"));

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getScore()).isLessThan(0.9);
  }

  @Test
  void embeddingMode_usedWhenClientProvidesEmbeddings() {
    double[] vec = new double[] {1.0, 0.0, 0.0};
    FakeLlmClient client = new FakeLlmClient("", vec);
    Evaluator ev = new SemanticSimilarityEvaluator(spec(Map.of("threshold", 0.5)), client);

    EvalResult result = ev.evaluate(caseWithGolden("any golden"), resp("any response"));

    assertThat(result.getDetails().get("mode")).isEqualTo("embedding");
    assertThat(result.getScore()).isCloseTo(1.0, offset(1e-9));
  }

  @Test
  void noGolden_failsWithMessage() {
    Evaluator ev = new SemanticSimilarityEvaluator(spec(Map.of()), null);

    EvalResult result = ev.evaluate(new EvalCase(), resp("something"));

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getDetails()).containsKey("error");
  }
}
