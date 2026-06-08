package io.aletheia.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.aletheia.runner.RunSummary;
import io.aletheia.runner.baseline.Regression;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes a {@link RunSummary} as a pretty-printed JSON file. */
public final class JsonReporter {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private JsonReporter() {}

  /**
   * Writes summary data to {@code outputPath}. Parent directories are created if absent.
   *
   * <p>Shape: {@code { totals, perEvaluator, totalLatencyMs, totalCostUsd, baselineDiff? }}
   */
  public static void write(RunSummary summary, Path outputPath) throws IOException {
    Files.createDirectories(outputPath.getParent() == null ? Path.of(".") : outputPath.getParent());

    Map<String, Object> root = new LinkedHashMap<>();

    Map<String, Object> totals = new LinkedHashMap<>();
    totals.put("cases", summary.getTotalCases());
    totals.put("passed", summary.getPassedCases());
    totals.put("failed", summary.getFailedCases());
    root.put("totals", totals);

    Map<String, Object> perEvaluator = new LinkedHashMap<>();
    summary
        .getEvaluatorStats()
        .forEach(
            (type, stats) -> {
              Map<String, Object> entry = new LinkedHashMap<>();
              entry.put("count", stats.getCount());
              entry.put("passed", stats.getPassed());
              entry.put("meanScore", stats.getMeanScore());
              entry.put("passRate", stats.getPassRate());
              perEvaluator.put(type, entry);
            });
    root.put("perEvaluator", perEvaluator);

    root.put("totalLatencyMs", summary.getTotalLatencyMs());
    root.put("totalCostUsd", summary.getTotalCostUsd());

    List<Regression> diff = summary.getBaselineDiff();
    if (diff != null) {
      List<Map<String, Object>> diffList = new ArrayList<>();
      for (Regression r : diff) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("caseId", r.getCaseId());
        entry.put("evaluatorType", r.getEvaluatorType());
        entry.put("previousScore", r.getPreviousScore());
        entry.put("currentScore", r.getCurrentScore());
        entry.put("delta", r.getDelta());
        diffList.add(entry);
      }
      root.put("baselineDiff", diffList);
    }

    MAPPER.writeValue(outputPath.toFile(), root);
  }
}
