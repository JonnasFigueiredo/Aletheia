package io.aletheia.runner;

import io.aletheia.client.LlmClient;
import io.aletheia.client.LlmClientFactory;
import io.aletheia.config.AletheiaConfig;
import io.aletheia.config.CostTable;
import io.aletheia.eval.Evaluator;
import io.aletheia.eval.Evaluators;
import io.aletheia.model.EvalCase;
import io.aletheia.model.EvalResult;
import io.aletheia.model.EvaluatorSpec;
import io.aletheia.model.LlmRequest;
import io.aletheia.model.LlmResponse;
import java.util.ArrayList;
import java.util.List;

/** Runs {@link EvalCase} instances against an {@link LlmClient} and aggregates results. */
public final class EvalRunner {

  private EvalRunner() {}

  /**
   * Runs a single eval case using {@code client}, computing cost via the default {@link CostTable}.
   */
  public static CaseResult runCase(EvalCase evalCase, LlmClient client) {
    return runCase(evalCase, client, CostTable.fromResource());
  }

  /** Runs a single eval case with an explicit {@link CostTable}. */
  public static CaseResult runCase(EvalCase evalCase, LlmClient client, CostTable costTable) {
    LlmRequest request =
        LlmRequest.builder(evalCase.getPrompt())
            .caseId(evalCase.getId())
            .system(evalCase.getSystem())
            .provider(evalCase.getProvider())
            .model(evalCase.getModel())
            .build();

    LlmResponse response = client.complete(request);

    String modelKey = evalCase.getModel() != null ? evalCase.getModel() : "";
    double cost = costTable.computeCost(modelKey, response.getTokensIn(), response.getTokensOut());

    List<EvalResult> evalResults = new ArrayList<>();
    for (EvaluatorSpec spec : evalCase.getEvaluators()) {
      try {
        Evaluator evaluator = Evaluators.forSpec(spec, client, costTable);
        evalResults.add(evaluator.evaluate(evalCase, response));
      } catch (Exception e) {
        evalResults.add(
            EvalResult.fail(
                spec.getType() != null ? spec.getType() : "unknown",
                "Evaluator construction error: " + e.getMessage()));
      }
    }

    return new CaseResult(evalCase, response, evalResults, response.getLatencyMs(), cost);
  }

  /**
   * Runs all cases, reusing {@code suiteClient} unless a case specifies a provider/model override.
   * Overrides are resolved via {@link LlmClientFactory} using environment-derived config as base.
   */
  public static RunSummary runSuite(List<EvalCase> cases, LlmClient suiteClient) {
    return runSuite(cases, suiteClient, AletheiaConfig.fromEnvironment());
  }

  /** Runs all cases with an explicit base config for provider override resolution. */
  public static RunSummary runSuite(
      List<EvalCase> cases, LlmClient suiteClient, AletheiaConfig baseConfig) {
    CostTable costTable = CostTable.fromResource();
    List<CaseResult> results = new ArrayList<>();

    for (EvalCase evalCase : cases) {
      LlmClient client = resolveClient(evalCase, suiteClient, baseConfig);
      results.add(runCase(evalCase, client, costTable));
    }

    return RunSummary.of(
        suiteClient.providerId(),
        baseConfig.getModel() != null ? baseConfig.getModel() : "unknown",
        results);
  }

  private static LlmClient resolveClient(
      EvalCase evalCase, LlmClient suiteClient, AletheiaConfig baseConfig) {
    if (evalCase.getProvider() == null && evalCase.getModel() == null) {
      return suiteClient;
    }
    String provider =
        evalCase.getProvider() != null ? evalCase.getProvider() : baseConfig.getProvider();
    String model = evalCase.getModel() != null ? evalCase.getModel() : baseConfig.getModel();
    AletheiaConfig overrideConfig =
        AletheiaConfig.of(
            provider,
            baseConfig.getBaseUrl(),
            baseConfig.getApiKey(),
            model,
            baseConfig.getTemperature(),
            baseConfig.getEmbedModel(),
            baseConfig.getFixturePath(),
            baseConfig.getTimeoutSeconds());
    try {
      return LlmClientFactory.fromConfig(overrideConfig);
    } catch (Exception e) {
      return suiteClient;
    }
  }
}
