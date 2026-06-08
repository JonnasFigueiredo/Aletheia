package io.aletheia.eval;

import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.model.LlmResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks that the response contains all required terms and none of the forbidden terms.
 *
 * <p>Score = matched_must_include / total_must_include (0.0–1.0). Passed only when score == 1.0 and
 * no must_not_include term is present.
 */
final class ContainsEvaluator implements Evaluator {

  private static final String TYPE = "contains";

  private final List<String> mustInclude;
  private final List<String> mustNotInclude;
  private final boolean caseSensitive;

  @SuppressWarnings("unchecked")
  ContainsEvaluator(EvaluatorSpec spec) {
    Map<String, Object> p = spec.getParams();
    this.mustInclude = toStringList(p.get("must_include"));
    this.mustNotInclude = toStringList(p.get("must_not_include"));
    Object cs = p.get("case_sensitive");
    this.caseSensitive = cs instanceof Boolean b && b;
  }

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public EvalResult evaluate(EvalCase evalCase, LlmResponse response) {
    String text = caseSensitive ? response.getText() : response.getText().toLowerCase();

    List<String> matched = new ArrayList<>();
    List<String> missed = new ArrayList<>();
    for (String term : mustInclude) {
      String needle = caseSensitive ? term : term.toLowerCase();
      if (text.contains(needle)) {
        matched.add(term);
      } else {
        missed.add(term);
      }
    }

    List<String> forbiddenFound = new ArrayList<>();
    for (String term : mustNotInclude) {
      String needle = caseSensitive ? term : term.toLowerCase();
      if (text.contains(needle)) {
        forbiddenFound.add(term);
      }
    }

    double score = mustInclude.isEmpty() ? 1.0 : (double) matched.size() / mustInclude.size();
    boolean passed = missed.isEmpty() && forbiddenFound.isEmpty();

    Map<String, Object> details = new HashMap<>();
    details.put("matched", matched);
    details.put("missed", missed);
    details.put("forbidden_found", forbiddenFound);

    return EvalResult.of(
        TYPE, passed, score, passed ? "All terms matched" : "Contains check failed", details);
  }

  private static List<String> toStringList(Object raw) {
    if (raw instanceof List<?> list) {
      List<String> result = new ArrayList<>(list.size());
      for (Object item : list) {
        if (item != null) {
          result.add(item.toString());
        }
      }
      return result;
    }
    return List.of();
  }
}
