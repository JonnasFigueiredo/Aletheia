package io.aletheia.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aletheia.model.EvalCase;
import io.aletheia.model.EvaluatorSpec;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link EvalCaseLoader}. */
class EvalCaseLoaderTest {

  @Test
  void loadFromFile_allFieldsRoundTrip() throws IOException, URISyntaxException {
    Path sample = sampleYamlPath();

    EvalCase evalCase = EvalCaseLoader.loadFromFile(sample);

    assertThat(evalCase.getId()).isEqualTo("sample-001");
    assertThat(evalCase.getDescription())
        .isEqualTo("Verify that the model can answer a basic factual question");
    assertThat(evalCase.getTags()).containsExactly("factual", "smoke");
    assertThat(evalCase.getPrompt()).isEqualTo("What is the capital of France?");
    assertThat(evalCase.getSystem())
        .isEqualTo("You are a helpful assistant. Answer concisely.");
    assertThat(evalCase.getContext()).isEqualTo("The question is about European geography.");
    assertThat(evalCase.getProvider()).isEqualTo("openai");
    assertThat(evalCase.getModel()).isEqualTo("gpt-4o-mini");
    assertThat(evalCase.getGolden()).isEqualTo("Paris");
    assertThat(evalCase.getJsonSchema()).isNotNull();
    assertThat(evalCase.getJsonSchema()).containsEntry("type", "object");
  }

  @Test
  void loadFromFile_evaluatorsRoundTrip() throws IOException, URISyntaxException {
    EvalCase evalCase = EvalCaseLoader.loadFromFile(sampleYamlPath());
    List<EvaluatorSpec> evaluators = evalCase.getEvaluators();

    assertThat(evaluators).hasSize(2);

    EvaluatorSpec first = evaluators.get(0);
    assertThat(first.getType()).isEqualTo("contains");
    assertThat(first.getParams()).containsEntry("substring", "Paris");
    assertThat(first.getParams()).containsEntry("case_sensitive", false);

    EvaluatorSpec second = evaluators.get(1);
    assertThat(second.getType()).isEqualTo("json_schema");
    assertThat(second.getParams()).containsEntry("strict", true);
  }

  @Test
  void loadFromDirectory_findsYamlFiles() throws IOException, URISyntaxException {
    URL dirUrl = getClass().getClassLoader().getResource("evalcases");
    assertThat(dirUrl).isNotNull();
    Path dir = Path.of(dirUrl.toURI());

    List<EvalCase> cases = EvalCaseLoader.loadFromDirectory(dir);

    assertThat(cases).hasSize(1);
    assertThat(cases.get(0).getId()).isEqualTo("sample-001");
  }

  @Test
  void loadFromFile_missingPrompt_throwsWithFileName(@TempDir Path tempDir) throws IOException {
    Path malformed = tempDir.resolve("malformed.yaml");
    Files.writeString(
        malformed,
        "id: bad-case-1\n"
            + "description: Missing prompt\n"
            + "evaluators:\n"
            + "  - type: contains\n"
            + "    substring: hello\n");

    assertThatThrownBy(() -> EvalCaseLoader.loadFromFile(malformed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("prompt")
        .hasMessageContaining("malformed.yaml");
  }

  private Path sampleYamlPath() throws URISyntaxException {
    URL url = getClass().getClassLoader().getResource("evalcases/sample.yaml");
    assertThat(url).isNotNull();
    return Path.of(url.toURI());
  }
}
