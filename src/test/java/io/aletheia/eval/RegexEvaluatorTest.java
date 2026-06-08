package io.aletheia.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.model.LlmResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegexEvaluatorTest {

  private static EvaluatorSpec spec(Map<String, Object> params) {
    EvaluatorSpec s = new EvaluatorSpec();
    s.setType("regex");
    params.forEach(s::addParam);
    return s;
  }

  private static LlmResponse resp(String text) {
    return new LlmResponse(text, 5, 10, 50);
  }

  private static final EvalCase EC = new EvalCase();

  @Test
  void missingPattern_throwsAtConstruction() {
    assertThatThrownBy(() -> new RegexEvaluator(spec(Map.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pattern");
  }

  @Test
  void basicPattern_matches() {
    Evaluator ev = new RegexEvaluator(spec(Map.of("pattern", "\\d{4}")));

    EvalResult result = ev.evaluate(EC, resp("Year 2024 was eventful"));

    assertThat(result.isPassed()).isTrue();
    assertThat(result.getScore()).isEqualTo(1.0);
    assertThat(result.getDetails().get("matched_substring")).isEqualTo("2024");
  }

  @Test
  void basicPattern_noMatch_fails() {
    Evaluator ev = new RegexEvaluator(spec(Map.of("pattern", "\\d{4}")));

    EvalResult result = ev.evaluate(EC, resp("No numbers here"));

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getScore()).isEqualTo(0.0);
  }

  @Test
  void ignoreCaseFlag_matchesCaseInsensitively() {
    Evaluator ev =
        new RegexEvaluator(spec(Map.of("pattern", "hello", "flags", List.of("IGNORE_CASE"))));

    assertThat(ev.evaluate(EC, resp("HELLO world")).isPassed()).isTrue();
    assertThat(ev.evaluate(EC, resp("Hello world")).isPassed()).isTrue();
    assertThat(ev.evaluate(EC, resp("goodbye")).isPassed()).isFalse();
  }

  @Test
  void multilineFlagComposedWithIgnoreCase() {
    Evaluator ev =
        new RegexEvaluator(
            spec(Map.of("pattern", "^start", "flags", List.of("IGNORE_CASE", "MULTILINE"))));

    EvalResult result = ev.evaluate(EC, resp("first line\nSTART of second"));

    assertThat(result.isPassed()).isTrue();
  }

  @Test
  void mustNotMatch_invertsSemantic() {
    Evaluator ev = new RegexEvaluator(spec(Map.of("pattern", "forbidden", "must_not_match", true)));

    assertThat(ev.evaluate(EC, resp("this is clean")).isPassed()).isTrue();
    assertThat(ev.evaluate(EC, resp("contains forbidden word")).isPassed()).isFalse();
  }
}
