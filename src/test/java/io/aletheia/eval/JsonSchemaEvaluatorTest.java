package io.aletheia.eval;

import static org.assertj.core.api.Assertions.assertThat;

import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.model.LlmResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonSchemaEvaluatorTest {

  private static EvaluatorSpec spec(Map<String, Object> params) {
    EvaluatorSpec s = new EvaluatorSpec();
    s.setType("json_schema");
    params.forEach(s::addParam);
    return s;
  }

  private static EvalCase caseWithSchema(Map<String, Object> schema) {
    EvalCase ec = new EvalCase();
    ec.setId("test");
    ec.setJsonSchema(schema);
    return ec;
  }

  private static LlmResponse resp(String text) {
    return new LlmResponse(text, 10, 20, 100);
  }

  @Test
  void validJsonMatchingSchema_passes() {
    Map<String, Object> schema =
        Map.of(
            "type", "object",
            "required", List.of("name"),
            "properties", Map.of("name", Map.of("type", "string")));
    Evaluator ev = new JsonSchemaEvaluator(spec(Map.of("schema", schema)));

    EvalResult result = ev.evaluate(new EvalCase(), resp("{\"name\": \"Alice\"}"));

    assertThat(result.isPassed()).isTrue();
    assertThat(result.getScore()).isEqualTo(1.0);
  }

  @Test
  void invalidJson_failsWithParseError() {
    Map<String, Object> schema = Map.of("type", "object");
    Evaluator ev = new JsonSchemaEvaluator(spec(Map.of("schema", schema)));

    EvalResult result = ev.evaluate(new EvalCase(), resp("not json at all {{{"));

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getScore()).isEqualTo(0.0);
    assertThat(result.getDetails()).containsKey("parseError");
  }

  @Test
  void validJsonMissingRequiredField_failsWithSchemaErrors() {
    Map<String, Object> schema =
        Map.of(
            "type",
            "object",
            "required",
            List.of("name", "age"),
            "properties",
            Map.of("name", Map.of("type", "string"), "age", Map.of("type", "integer")));
    Evaluator ev = new JsonSchemaEvaluator(spec(Map.of("schema", schema)));

    EvalResult result = ev.evaluate(new EvalCase(), resp("{\"name\": \"Bob\"}"));

    assertThat(result.isPassed()).isFalse();
    @SuppressWarnings("unchecked")
    List<String> errors = (List<String>) result.getDetails().get("errors");
    assertThat(errors).isNotEmpty();
  }

  @Test
  void schemaFromEvalCase_usedWhenSpecHasNoSchema() {
    Map<String, Object> schema =
        Map.of(
            "type", "object",
            "required", List.of("city"),
            "properties", Map.of("city", Map.of("type", "string")));
    EvalCase ec = caseWithSchema(schema);
    Evaluator ev = new JsonSchemaEvaluator(spec(Map.of()));

    EvalResult passResult = ev.evaluate(ec, resp("{\"city\": \"Rome\"}"));
    EvalResult failResult = ev.evaluate(ec, resp("{\"country\": \"Italy\"}"));

    assertThat(passResult.isPassed()).isTrue();
    assertThat(failResult.isPassed()).isFalse();
  }

  @Test
  void noSchemaAnywhere_returnsConfigError() {
    Evaluator ev = new JsonSchemaEvaluator(spec(Map.of()));

    EvalResult result = ev.evaluate(new EvalCase(), resp("{\"x\": 1}"));

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getDetails()).containsKey("error");
  }
}
