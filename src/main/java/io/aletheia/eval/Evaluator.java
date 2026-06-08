package io.aletheia.eval;

import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.LlmResponse;

/** Assertion applied to every LLM response within an eval case. */
public interface Evaluator {

  /** Stable type identifier, e.g. {@code "json_schema"} or {@code "contains"}. */
  String type();

  /**
   * Evaluates {@code response} against the expectations encoded in {@code evalCase}.
   *
   * <p>Never throws on an assertion failure — use the returned {@link EvalResult}. Only throws
   * {@link IllegalArgumentException} for configuration errors (e.g. missing required parameters).
   */
  EvalResult evaluate(EvalCase evalCase, LlmResponse response);
}
