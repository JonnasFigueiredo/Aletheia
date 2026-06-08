package io.aletheia.eval;

import static org.assertj.core.api.Assertions.assertThat;

import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.model.LlmResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContainsEvaluatorTest {

  private static EvaluatorSpec spec(Map<String, Object> params) {
    EvaluatorSpec s = new EvaluatorSpec();
    s.setType("contains");
    params.forEach(s::addParam);
    return s;
  }

  private static LlmResponse resp(String text) {
    return new LlmResponse(text, 5, 10, 50);
  }

  private static final EvalCase EC = new EvalCase();

  @Test
  void caseInsensitiveMatchByDefault_passes() {
    Evaluator ev = new ContainsEvaluator(spec(Map.of("must_include", List.of("HELLO", "world"))));

    EvalResult result = ev.evaluate(EC, resp("Hello World, how are you?"));

    assertThat(result.isPassed()).isTrue();
    assertThat(result.getScore()).isEqualTo(1.0);
  }

  @Test
  void caseSensitive_doesNotMatchWrongCase() {
    Evaluator ev =
        new ContainsEvaluator(
            spec(Map.of("must_include", List.of("Hello"), "case_sensitive", true)));

    EvalResult pass = ev.evaluate(EC, resp("Hello there"));
    EvalResult fail = ev.evaluate(EC, resp("hello there"));

    assertThat(pass.isPassed()).isTrue();
    assertThat(fail.isPassed()).isFalse();
  }

  @Test
  void mustNotInclude_tripsFail() {
    Evaluator ev =
        new ContainsEvaluator(
            spec(
                Map.of(
                    "must_include", List.of("good"),
                    "must_not_include", List.of("bad"))));

    EvalResult result = ev.evaluate(EC, resp("This is good and also bad"));

    assertThat(result.isPassed()).isFalse();
    @SuppressWarnings("unchecked")
    List<String> forbidden = (List<String>) result.getDetails().get("forbidden_found");
    assertThat(forbidden).contains("bad");
  }

  @Test
  void partialMatchScore_arithmeticCorrect() {
    Evaluator ev =
        new ContainsEvaluator(spec(Map.of("must_include", List.of("alpha", "beta", "gamma"))));

    EvalResult result = ev.evaluate(EC, resp("alpha and beta are here but not the third"));

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getScore()).isCloseTo(2.0 / 3.0, org.assertj.core.data.Offset.offset(1e-9));
    @SuppressWarnings("unchecked")
    List<String> missed = (List<String>) result.getDetails().get("missed");
    assertThat(missed).containsExactly("gamma");
  }

  @Test
  void emptyMustInclude_passesWithFullScore() {
    Evaluator ev = new ContainsEvaluator(spec(Map.of("must_include", List.of())));

    EvalResult result = ev.evaluate(EC, resp("anything"));

    assertThat(result.isPassed()).isTrue();
    assertThat(result.getScore()).isEqualTo(1.0);
  }
}
