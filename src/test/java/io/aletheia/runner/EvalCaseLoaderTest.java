package io.aletheia.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aletheia.model.EvalCase;
import io.aletheia.model.EvaluatorSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvalCaseLoaderTest {

  @Test
  void loadsAllFieldsFromSampleYaml() throws IOException {
    Path sample =
        Path.of(
            EvalCaseLoaderTest.class
                .getClassLoader()
                .getResource("evalcases/sample.yaml")
                .toURI());

    EvalCase evalCase = EvalCaseLoader.loadFromFile(sample);

    assertThat(evalCase.getId()).isEqualTo("sample-001");
    assertThat(evalCase.getDescription())
        .isEqualTo("Verify that the model can answer a basic factual question");
    assertThat(evalCase.getTags()).containsExactly("factual", "smoke");
    assertThat(evalCase.getPrompt()).isEqualTo("What is the capital of France?");
    assertThat(evalCase.getSystem()).isEqualTo("You are a helpful assistant. Answer concisely.");
    assertThat(evalCase.getContext()).isEqualTo("The question is about European geography.");
    assertThat(evalCase.getProvider()).isEqualTo("openai");
    assertThat(evalCase.getModel()).isEqualTo("gpt-4o-mini");
    assertThat(evalCase.getGolden()).isEqualTo("Paris");
    assertThat(evalCase.getJsonSchema()).isNotNull();
  }

  @Test
  void loadsPolymorphicEvaluators() throws IOException {
    Path sample =
        Path.of(
            EvalCaseLoaderTest.class
                .getClassLoader()
                .getResource("evalcases/sample.yaml")
                .toURI());

    EvalCase evalCase = EvalCaseLoader.loadFromFile(sample);
    List<EvaluatorSpec> evaluators = evalCase.getEvaluators();

    assertThat(evaluators).hasSize(2);

    EvaluatorSpec contains = evaluators.get(0);
    assertThat(contains.getType()).isEqualTo("contains");
    assertThat(contains.getParams()).containsEntry("substring", "Paris");
    assertThat(contains.getParams()).containsEntry("case_sensitive", false);

    EvaluatorSpec schema = evaluators.get(1);
    assertThat(schema.getType()).isEqualTo("json_schema");
    assertThat(schema.getParams()).containsEntry("strict", true);
  }

  @Test
  void loadFromDirectoryReturnsAllYamlFiles(@TempDir Path tempDir) throws IOException {
    Path sampleSrc =
        Path.of(
            EvalCaseLoaderTest.class
                .getClassLoader()
                .getResource("evalcases/sample.yaml")
                .toURI());
    Files.copy(sampleSrc, tempDir.resolve("case1.yaml"));
    Files.copy(sampleSrc, tempDir.resolve("case2.yaml"));
    Files.writeString(tempDir.resolve("not-a-case.txt"), "ignored");

    List<EvalCase> cases = EvalCaseLoader.loadFromDirectory(tempDir);

    assertThat(cases).hasSize(2);
    assertThat(cases).allMatch(c -> "sample-001".equals(c.getId()));
  }

  @Test
  void throwsWhenPromptIsMissing(@TempDir Path tempDir) throws IOException {
    Path malformed = tempDir.resolve("bad.yaml");
    Files.writeString(
        malformed,
        "id: bad-001\nevaluators:\n  - type: contains\n    substring: foo\n");

    assertThatThrownBy(() -> EvalCaseLoader.loadFromFile(malformed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("prompt");
  }

  @Test
  void throwsWhenIdIsMissing(@TempDir Path tempDir) throws IOException {
    Path malformed = tempDir.resolve("no-id.yaml");
    Files.writeString(
        malformed,
        "prompt: \"hello\"\nevaluators:\n  - type: contains\n    substring: foo\n");

    assertThatThrownBy(() -> EvalCaseLoader.loadFromFile(malformed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id");
  }

  @Test
  void throwsWhenNoEvaluators(@TempDir Path tempDir) throws IOException {
    Path malformed = tempDir.resolve("no-evals.yaml");
    Files.writeString(malformed, "id: test-001\nprompt: \"hello\"\nevaluators: []\n");

    assertThatThrownBy(() -> EvalCaseLoader.loadFromFile(malformed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("evaluator");
  }
}
