package io.aletheia.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Specifies a single evaluator to apply to an LLM response. */
public final class EvaluatorSpec {

  @JsonProperty("type")
  private String type;

  private final Map<String, Object> params = new HashMap<>();

  public EvaluatorSpec() {}

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  /** Captures any YAML key besides "type" as evaluator parameters. */
  @JsonAnySetter
  public void addParam(String key, Object value) {
    params.put(key, value);
  }

  public Map<String, Object> getParams() {
    return Collections.unmodifiableMap(params);
  }

  @Override
  public String toString() {
    return "EvaluatorSpec{type='" + type + "', params=" + params + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EvaluatorSpec)) return false;
    EvaluatorSpec that = (EvaluatorSpec) o;
    return Objects.equals(type, that.type) && Objects.equals(params, that.params);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, params);
  }
}
