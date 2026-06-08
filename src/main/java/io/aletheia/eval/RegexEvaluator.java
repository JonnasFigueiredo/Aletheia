package io.aletheia.eval;

import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.model.LlmResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies a regular expression to the response text, with optional flag composition. */
final class RegexEvaluator implements Evaluator {

  private static final String TYPE = "regex";

  private final Pattern pattern;
  private final boolean mustNotMatch;

  RegexEvaluator(EvaluatorSpec spec) {
    Map<String, Object> p = spec.getParams();
    Object rawPattern = p.get("pattern");
    if (rawPattern == null) {
      throw new IllegalArgumentException("RegexEvaluator requires a 'pattern' parameter");
    }

    List<String> flagNames = toStringList(p.get("flags"));
    int flags = buildFlags(flagNames);
    this.pattern = Pattern.compile(rawPattern.toString(), flags);

    Object mnm = p.get("must_not_match");
    this.mustNotMatch = mnm instanceof Boolean b && b;
  }

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public EvalResult evaluate(EvalCase evalCase, LlmResponse response) {
    Matcher matcher = pattern.matcher(response.getText());
    boolean found = matcher.find();
    boolean passed = mustNotMatch ? !found : found;
    double score = passed ? 1.0 : 0.0;

    Map<String, Object> details = new HashMap<>();
    details.put("pattern", pattern.pattern());
    details.put("matches", found);
    details.put("must_not_match", mustNotMatch);
    if (found && !mustNotMatch) {
      details.put("matched_substring", matcher.group());
    }

    return EvalResult.of(
        TYPE, passed, score, passed ? "Regex check passed" : "Regex check failed", details);
  }

  private static int buildFlags(List<String> flagNames) {
    int flags = 0;
    for (String name : flagNames) {
      switch (name) {
        case "IGNORE_CASE" -> flags |= Pattern.CASE_INSENSITIVE;
        case "MULTILINE" -> flags |= Pattern.MULTILINE;
        case "DOTALL" -> flags |= Pattern.DOTALL;
        default -> throw new IllegalArgumentException("Unknown regex flag: " + name);
      }
    }
    return flags;
  }

  private static List<String> toStringList(Object raw) {
    if (raw instanceof List<?> list) {
      return list.stream().filter(o -> o != null).map(Object::toString).toList();
    }
    return List.of();
  }
}
