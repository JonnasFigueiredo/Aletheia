package io.aletheia.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A single evaluation case loaded from a YAML file. */
public final class EvalCase {

  @JsonProperty("id")
  private String id;

  @JsonProperty("description")
  private String description;

  @JsonProperty("tags")
  private List<String> tags;

  @JsonProperty("prompt")
  private String prompt;

  @JsonProperty("system")
  private String system;

  @JsonProperty("context")
  private String context;

  @JsonProperty("provider")
  private String provider;

  @JsonProperty("model")
  private String model;

  @JsonProperty("golden")
  private String golden;

  @JsonProperty("json_schema")
  private Map<String, Object> jsonSchema;

  @JsonProperty("evaluators")
  private List<EvaluatorSpec> evaluators;

  public EvalCase() {}

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public List<String> getTags() {
    return tags == null ? Collections.emptyList() : Collections.unmodifiableList(tags);
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public String getPrompt() {
    return prompt;
  }

  public void setPrompt(String prompt) {
    this.prompt = prompt;
  }

  public String getSystem() {
    return system;
  }

  public void setSystem(String system) {
    this.system = system;
  }

  public String getContext() {
    return context;
  }

  public void setContext(String context) {
    this.context = context;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getGolden() {
    return golden;
  }

  public void setGolden(String golden) {
    this.golden = golden;
  }

  public Map<String, Object> getJsonSchema() {
    return jsonSchema == null ? null : Collections.unmodifiableMap(jsonSchema);
  }

  public void setJsonSchema(Map<String, Object> jsonSchema) {
    this.jsonSchema = jsonSchema;
  }

  public List<EvaluatorSpec> getEvaluators() {
    return evaluators == null ? Collections.emptyList() : Collections.unmodifiableList(evaluators);
  }

  public void setEvaluators(List<EvaluatorSpec> evaluators) {
    this.evaluators = evaluators;
  }

  @Override
  public String toString() {
    return "EvalCase{id='" + id + "', prompt='" + prompt + "'}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EvalCase)) return false;
    EvalCase that = (EvalCase) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
