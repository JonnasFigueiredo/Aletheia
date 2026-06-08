package io.aletheia.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.model.LlmResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RubricJudgeEvaluatorTest {

  private static EvaluatorSpec spec(Map<String, Object> params) {
    EvaluatorSpec s = new EvaluatorSpec();
    s.setType("rubric_judge");
    params.forEach(s::addParam);
    return s;
  }

  private static LlmResponse resp(String text) {
    return new LlmResponse(text, 10, 30, 200);
  }

  private static final EvalCase EC = new EvalCase();

  @Test
  void judgeMode_highScores_passes() {
    String judgeJson =
        "{\"clarity\": {\"score\": 5, \"rationale\": \"very clear\"},"
            + "\"correctness\": {\"score\": 4, \"rationale\": \"mostly correct\"},"
            + "\"completeness\": {\"score\": 5, \"rationale\": \"thorough\"}}";
    Evaluator ev =
        new RubricJudgeEvaluator(spec(Map.of("threshold", 0.6)), new FakeLlmClient(judgeJson));

    EvalResult result = ev.evaluate(EC, resp("A detailed and correct answer."));

    assertThat(result.isPassed()).isTrue();
    // mean = (5+4+5)/3 = 4.667, score = 4.667/5 ≈ 0.933
    assertThat(result.getScore()).isGreaterThan(0.6);
    assertThat(result.getDetails()).containsKey("criteria");
  }

  @Test
  void judgeMode_lowScores_fails() {
    String judgeJson =
        "{\"clarity\": {\"score\": 1, \"rationale\": \"confusing\"},"
            + "\"correctness\": {\"score\": 1, \"rationale\": \"wrong\"},"
            + "\"completeness\": {\"score\": 1, \"rationale\": \"incomplete\"}}";
    Evaluator ev =
        new RubricJudgeEvaluator(spec(Map.of("threshold", 0.6)), new FakeLlmClient(judgeJson));

    EvalResult result = ev.evaluate(EC, resp("Bad answer."));

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getScore()).isCloseTo(1.0 / 5.0, offset(1e-9));
  }

  @Test
  void malformedJudgeJson_fallsBackToHeuristic() {
    Evaluator ev = new RubricJudgeEvaluator(spec(Map.of()), new FakeLlmClient("not { valid json"));

    EvalResult result = ev.evaluate(EC, resp("Any response text here."));

    assertThat(result.getDetails().get("mode")).isEqualTo("heuristic_length_proxy");
  }

  @Test
  void mockClient_usesHeuristicDirectly() {
    Evaluator ev = new RubricJudgeEvaluator(spec(Map.of()), FakeLlmClient.asMock("{}"));

    EvalResult result = ev.evaluate(EC, resp("Some response."));

    assertThat(result.getDetails().get("mode")).isEqualTo("heuristic_length_proxy");
  }

  @Test
  void heuristic_longResponse_capsAt60Percent() {
    Evaluator ev = new RubricJudgeEvaluator(spec(Map.of("threshold", 0.5)), null);

    // 1000 chars → min(0.6, 1000/500) = 0.6
    String longText = "x".repeat(1000);
    EvalResult result = ev.evaluate(EC, resp(longText));

    assertThat(result.getScore()).isCloseTo(0.6, offset(1e-9));
    assertThat(result.isPassed()).isTrue();
  }

  @Test
  void customRubric_usedWhenProvided() {
    Map<String, Object> rubric = Map.of("helpfulness", "Is the response helpful?");
    String judgeJson = "{\"helpfulness\": {\"score\": 4, \"rationale\": \"helpful\"}}";
    Evaluator ev =
        new RubricJudgeEvaluator(
            spec(Map.of("rubric", rubric, "threshold", 0.5)), new FakeLlmClient(judgeJson));

    EvalResult result = ev.evaluate(EC, resp("A helpful response."));

    assertThat(result.isPassed()).isTrue();
    assertThat(result.getScore()).isCloseTo(4.0 / 5.0, offset(1e-9));
  }
}
