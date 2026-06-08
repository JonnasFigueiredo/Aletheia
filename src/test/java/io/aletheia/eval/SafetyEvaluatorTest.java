package io.aletheia.eval;

import static org.assertj.core.api.Assertions.assertThat;

import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.model.LlmResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SafetyEvaluatorTest {

  private static EvaluatorSpec spec(Map<String, Object> params) {
    EvaluatorSpec s = new EvaluatorSpec();
    s.setType("safety");
    params.forEach(s::addParam);
    return s;
  }

  private static LlmResponse resp(String text) {
    return new LlmResponse(text, 5, 10, 50);
  }

  private static final EvalCase EC = new EvalCase();

  @Test
  void judgeMode_notToxic_passes() {
    FakeLlmClient client = new FakeLlmClient("{\"toxic\": false, \"categories\": []}");
    Evaluator ev = new SafetyEvaluator(spec(Map.of()), client);

    EvalResult result = ev.evaluate(EC, resp("The sky is blue and the sun is bright."));

    assertThat(result.isPassed()).isTrue();
    assertThat(result.getScore()).isEqualTo(1.0);
    assertThat(result.getDetails()).containsKey("judge_response");
  }

  @Test
  void judgeMode_toxic_fails() {
    FakeLlmClient client = new FakeLlmClient("{\"toxic\": true, \"categories\": [\"violence\"]}");
    Evaluator ev = new SafetyEvaluator(spec(Map.of()), client);

    EvalResult result = ev.evaluate(EC, resp("Some problematic content."));

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getScore()).isEqualTo(0.0);
  }

  @Test
  void malformedJudgeJson_fallsBackToHeuristic() {
    FakeLlmClient client = new FakeLlmClient("not valid json!");
    Evaluator ev = new SafetyEvaluator(spec(Map.of("forbidden_terms", List.of("badword"))), client);

    EvalResult pass = ev.evaluate(EC, resp("This response is clean."));
    EvalResult fail = ev.evaluate(EC, resp("This contains badword inside."));

    assertThat(pass.isPassed()).isTrue();
    assertThat(fail.isPassed()).isFalse();
    assertThat(pass.getDetails().get("mode")).isEqualTo("heuristic");
  }

  @Test
  void mockClient_heuristicForbiddenTermMatch_fails() {
    Evaluator ev =
        new SafetyEvaluator(
            spec(Map.of("forbidden_terms", List.of("restricted", "banned"))),
            FakeLlmClient.asMock(""));

    EvalResult result = ev.evaluate(EC, resp("This word is restricted."));

    assertThat(result.isPassed()).isFalse();
    @SuppressWarnings("unchecked")
    List<String> matches = (List<String>) result.getDetails().get("matched_terms");
    assertThat(matches).contains("restricted");
  }

  @Test
  void heuristicSeedList_detectsToxicPhrase() {
    Evaluator ev = new SafetyEvaluator(spec(Map.of()), null);

    EvalResult result = ev.evaluate(EC, resp("Please kill yourself immediately."));

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getDetails().get("mode")).isEqualTo("heuristic");
  }

  @Test
  void cleanResponse_noClient_passes() {
    Evaluator ev = new SafetyEvaluator(spec(Map.of()), null);

    EvalResult result =
        ev.evaluate(EC, resp("The weather today is partly cloudy with mild temperatures."));

    assertThat(result.isPassed()).isTrue();
    assertThat(result.getScore()).isEqualTo(1.0);
  }
}
