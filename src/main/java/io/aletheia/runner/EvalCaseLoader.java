package io.aletheia.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.aletheia.model.EvalCase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Loads {@link EvalCase} instances from YAML files. */
public final class EvalCaseLoader {

  private static final ObjectMapper MAPPER =
      new ObjectMapper(new YAMLFactory())
          .findAndRegisterModules()
          .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

  private EvalCaseLoader() {}

  /**
   * Loads all {@code *.yaml} files found directly under {@code dir}.
   *
   * @throws IOException if any file cannot be read or parsed
   */
  public static List<EvalCase> loadFromDirectory(Path dir) throws IOException {
    List<EvalCase> cases = new ArrayList<>();
    try (Stream<Path> files = Files.list(dir)) {
      for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".yaml")).toList()) {
        cases.add(loadFromFile(file));
      }
    }
    return cases;
  }

  /**
   * Loads a single {@link EvalCase} from a YAML file.
   *
   * @throws IOException if the file cannot be read or parsed
   * @throws IllegalArgumentException if required fields are missing
   */
  public static EvalCase loadFromFile(Path file) throws IOException {
    EvalCase evalCase = MAPPER.readValue(file.toFile(), EvalCase.class);
    validate(file, evalCase);
    return evalCase;
  }

  private static void validate(Path file, EvalCase evalCase) {
    if (evalCase.getId() == null || evalCase.getId().isBlank()) {
      throw new IllegalArgumentException("EvalCase in " + file + " is missing required field: id");
    }
    if (evalCase.getPrompt() == null || evalCase.getPrompt().isBlank()) {
      throw new IllegalArgumentException(
          "EvalCase '" + evalCase.getId() + "' in " + file + " is missing required field: prompt");
    }
    if (evalCase.getEvaluators().isEmpty()) {
      throw new IllegalArgumentException(
          "EvalCase '" + evalCase.getId() + "' in " + file + " must have at least one evaluator");
    }
  }
}
