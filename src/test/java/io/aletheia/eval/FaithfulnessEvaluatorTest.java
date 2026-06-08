package io.aletheia.eval;

import static org.assertj.core.api.Assertions.assertThat;

import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.model.LlmResponse;
import org.junit.jupiter.api.Test;

class FaithfulnessEvaluatorTest {

  private static EvaluatorSpec spec() {
    EvaluatorSpec s = new EvaluatorSpec();
    s.setType("faithfulness");
    return s;
  }

  private static EvalCase caseWithContext(String context) {
    EvalCase ec = new EvalCase();
    ec.setId("test");
    ec.setContext(context);
    return ec;
  }

  private static LlmResponse resp(String text) {
    return new LlmResponse(text, 10, 20, 100);
  }

  @Test
  void judgeMode_supportedTrue_passes() {
    FakeLlmClient client =
        new FakeLlmClient(
            "{\"supported\": true, \"score\": 0.95, \"reasons\": [\"well grounded\"]}");
    Evaluator ev = new FaithfulnessEvaluator(spec(), client);

    EvalResult result =
        ev.evaluate(
            caseWithContext("The sky is blue. Water is wet."), resp("The sky appears blue."));

    assertThat(result.isPassed()).isTrue();
    assertThat(result.getScore()).isEqualTo(0.95);
    assertThat(result.getDetails()).containsKey("judge_prompt");
    assertThat(result.getDetails()).containsKey("judge_response");
  }

  @Test
  void judgeMode_supportedFalse_fails() {
    FakeLlmClient client =
        new FakeLlmClient(
            "{\"supported\": false, \"score\": 0.1, \"reasons\": [\"not grounded\"]}");
    Evaluator ev = new FaithfulnessEvaluator(spec(), client);

    EvalResult result =
        ev.evaluate(
            caseWithContext("Water boils at 100 degrees."), resp("Water freezes at 100 degrees."));

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getScore()).isEqualTo(0.1);
  }

  @Test
  void malformedJudgeJson_fallsBackToHeuristic() {
    FakeLlmClient client = new FakeLlmClient("not valid json {{{{");
    Evaluator ev = new FaithfulnessEvaluator(spec(), client);

    EvalResult result =
        ev.evaluate(
            caseWithContext("The capital of France is Paris."),
            resp("Paris is the capital of France."));

    assertThat(result.getDetails().get("mode")).isEqualTo("heuristic");
  }

  @Test
  void mockClient_usesHeuristicDirectly() {
    FakeLlmClient client = FakeLlmClient.asMock("{\"supported\": true}");
    Evaluator ev = new FaithfulnessEvaluator(spec(), client);

    EvalResult result =
        ev.evaluate(caseWithContext("The cat sat on the mat."), resp("The cat sat on the mat."));

    assertThat(result.getDetails().get("mode")).isEqualTo("heuristic");
    assertThat(result.isPassed()).isTrue();
  }

  @Test
  void heuristic_highOverlapWithContext_passes() {
    Evaluator ev = new FaithfulnessEvaluator(spec(), null);

    EvalResult result =
        ev.evaluate(
            caseWithContext("Photosynthesis is the process by which plants convert sunlight."),
            resp("Plants convert sunlight through photosynthesis."));

    assertThat(result.getDetails().get("mode")).isEqualTo("heuristic");
    assertThat(result.isPassed()).isTrue();
  }

  @Test
  void heuristic_contradictionMarker_fails() {
    Evaluator ev = new FaithfulnessEvaluator(spec(), null);

    EvalResult result =
        ev.evaluate(
            caseWithContext("Water is wet."),
            resp("This contradicts established science, goes against what the context says."));

    assertThat(result.getDetails().get("mode")).isEqualTo("heuristic");
    assertThat(result.isPassed()).isFalse();
    assertThat((Boolean) result.getDetails().get("has_contradiction_marker")).isTrue();
  }
}
