package io.aletheia.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.model.LlmResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates {@link LlmResponse#getText()} as JSON against a JSON Schema (Draft-07). */
final class JsonSchemaEvaluator implements Evaluator {

  private static final String TYPE = "json_schema";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final JsonSchemaFactory FACTORY =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

  /** Pre-compiled schema from the spec {@code schema} param; null means use evalCase.jsonSchema. */
  private final JsonSchema cachedSchema;

  JsonSchemaEvaluator(EvaluatorSpec spec) {
    Object raw = spec.getParams().get("schema");
    if (raw instanceof Map<?, ?> m) {
      JsonNode node = MAPPER.convertValue(m, JsonNode.class);
      this.cachedSchema = FACTORY.getSchema(node);
    } else {
      this.cachedSchema = null;
    }
  }

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public EvalResult evaluate(EvalCase evalCase, LlmResponse response) {
    JsonSchema schema = resolveSchema(evalCase);
    if (schema == null) {
      Map<String, Object> details = new HashMap<>();
      details.put("error", "Neither spec.schema nor evalCase.jsonSchema is set");
      return EvalResult.of(TYPE, false, 0.0, "No schema configured", details);
    }

    JsonNode responseNode;
    try {
      responseNode = MAPPER.readTree(response.getText());
    } catch (Exception e) {
      Map<String, Object> details = new HashMap<>();
      details.put("parseError", e.getMessage());
      return EvalResult.of(TYPE, false, 0.0, "Response is not valid JSON", details);
    }

    Set<ValidationMessage> errors = schema.validate(responseNode);
    if (errors.isEmpty()) {
      return EvalResult.of(TYPE, true, 1.0, "Schema validation passed", new HashMap<>());
    }

    List<String> messages = new ArrayList<>();
    for (ValidationMessage vm : errors) {
      messages.add(vm.getMessage());
    }
    Map<String, Object> details = new HashMap<>();
    details.put("errors", messages);
    return EvalResult.of(TYPE, false, 0.0, "Schema validation failed", details);
  }

  private JsonSchema resolveSchema(EvalCase evalCase) {
    if (cachedSchema != null) {
      return cachedSchema;
    }
    Map<String, Object> caseSchema = evalCase.getJsonSchema();
    if (caseSchema != null) {
      JsonNode node = MAPPER.convertValue(caseSchema, JsonNode.class);
      return FACTORY.getSchema(node);
    }
    return null;
  }
}
