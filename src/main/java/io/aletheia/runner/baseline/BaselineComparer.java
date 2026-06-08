package io.aletheia.runner.baseline;

import io.aletheia.model.EvalResult;
import io.aletheia.runner.CaseResult;
import io.aletheia.runner.RunSummary;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares a {@link RunSummary} against a stored {@link BaselineSnapshot} and reports regressions.
 */
public final class BaselineComparer {

  private BaselineComparer() {}

  /**
   * Returns every evaluator whose current score is lower than the baseline score by more than
   * {@code tolerance}. Missing baseline entries are skipped.
   */
  public static List<Regression> compare(
      RunSummary current, BaselineSnapshot baseline, double tolerance) {
    Map<String, Map<String, Double>> baselineScores = buildLookup(baseline);
    List<Regression> regressions = new ArrayList<>();

    for (CaseResult cr : current.getCaseResults()) {
      String caseId = cr.getEvalCase().getId();
      Map<String, Double> caseBaseline = baselineScores.getOrDefault(caseId, Map.of());
      for (EvalResult er : cr.getEvalResults()) {
        String type = er.getEvaluatorType();
        if (!caseBaseline.containsKey(type)) {
          continue;
        }
        double prev = caseBaseline.get(type);
        double curr = er.getScore();
        if (curr < prev - tolerance) {
          regressions.add(new Regression(caseId, type, prev, curr));
        }
      }
    }
    return regressions;
  }

  private static Map<String, Map<String, Double>> buildLookup(BaselineSnapshot snapshot) {
    Map<String, Map<String, Double>> lookup = new HashMap<>();
    for (BaselineSnapshot.CaseBaseline cb : snapshot.getCases()) {
      Map<String, Double> evalScores = new HashMap<>();
      for (BaselineSnapshot.EvaluatorBaseline eb : cb.getEvaluators()) {
        evalScores.put(eb.getType(), eb.getScore());
      }
      lookup.put(cb.getCaseId(), evalScores);
    }
    return lookup;
  }
}
