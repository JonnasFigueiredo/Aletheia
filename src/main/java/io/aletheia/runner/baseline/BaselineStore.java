package io.aletheia.runner.baseline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.aletheia.model.EvalResult;
import io.aletheia.runner.CaseResult;
import io.aletheia.runner.RunSummary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Persists and loads {@link BaselineSnapshot} JSON files. */
public final class BaselineStore {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private BaselineStore() {}

  /**
   * Returns the default path for a baseline: {@code baselines/<provider>-<model>.json}.
   *
   * <p>When provider or model is {@code null}, {@code "unknown"} is substituted.
   */
  public static Path defaultPath(String provider, String model) {
    String p = provider != null ? provider : "unknown";
    String m = model != null ? model : "unknown";
    return Path.of("baselines", p + "-" + m + ".json");
  }

  /** Loads a {@link BaselineSnapshot} from {@code path}. */
  public static BaselineSnapshot load(Path path) throws IOException {
    return MAPPER.readValue(path.toFile(), BaselineSnapshot.class);
  }

  /** Serialises {@code summary} as a {@link BaselineSnapshot} and writes it to {@code path}. */
  public static void save(Path path, RunSummary summary) throws IOException {
    Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());
    BaselineSnapshot snapshot = toSnapshot(summary);
    MAPPER.writeValue(path.toFile(), snapshot);
  }

  private static BaselineSnapshot toSnapshot(RunSummary summary) {
    List<BaselineSnapshot.CaseBaseline> cases = new ArrayList<>();
    for (CaseResult cr : summary.getCaseResults()) {
      List<BaselineSnapshot.EvaluatorBaseline> evals = new ArrayList<>();
      for (EvalResult er : cr.getEvalResults()) {
        evals.add(
            new BaselineSnapshot.EvaluatorBaseline(
                er.getEvaluatorType(), er.getScore(), er.isPassed()));
      }
      cases.add(new BaselineSnapshot.CaseBaseline(cr.getEvalCase().getId(), evals));
    }
    return new BaselineSnapshot(
        summary.getProvider(), summary.getModel(), Instant.now().toString(), cases);
  }
}
