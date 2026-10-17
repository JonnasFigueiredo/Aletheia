package io.aletheia;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.aletheia.client.LlmClient;
import io.aletheia.client.LlmClientFactory;
import io.aletheia.config.AletheiaConfig;
import io.aletheia.model.EvalCase;
import io.aletheia.runner.EvalCaseLoader;
import io.aletheia.runner.EvalRunner;
import io.aletheia.runner.RunSummary;
import io.aletheia.runner.baseline.BaselineComparer;
import io.aletheia.runner.baseline.BaselineSnapshot;
import io.aletheia.runner.baseline.BaselineStore;
import io.aletheia.runner.baseline.Regression;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Compares the current run against a stored baseline. Only active when the system property {@code
 * aletheia.baseline.enforce=true} is set; otherwise the test is skipped.
 */
class AletheiaBaselineTest {

  private static final String ENFORCE_PROP = "aletheia.baseline.enforce";
  private static final String CASES_DIR_PROP = "aletheia.cases.dir";
  private static final double DEFAULT_TOLERANCE = 0.05;

  @Test
  void noRegressionsVsBaseline() throws IOException {
    assumeTrue(
        "true".equalsIgnoreCase(System.getProperty(ENFORCE_PROP)),
        "Baseline enforcement disabled; set -Daletheia.baseline.enforce=true to enable");

    AletheiaConfig cfg = AletheiaConfig.fromEnvironment();
    LlmClient client = LlmClientFactory.fromConfig(cfg);

    String casesDir = System.getProperty(CASES_DIR_PROP, "evalcases");
    Path dir = Path.of(casesDir);
    assertTrue(Files.isDirectory(dir), "Cases directory does not exist: " + dir);

    List<EvalCase> cases = EvalCaseLoader.loadFromDirectory(dir);
    assertTrue(!cases.isEmpty(), "No eval cases found in " + dir);

    RunSummary current = EvalRunner.runSuite(cases, client, cfg);
    Path baselinePath = BaselineStore.defaultPath(current.getProvider(), current.getModel());

    assertTrue(
        baselinePath.toFile().exists(),
        "No baseline found at " + baselinePath + "; run record-baseline first");

    BaselineSnapshot baseline = BaselineStore.load(baselinePath);
    List<Regression> regressions = BaselineComparer.compare(current, baseline, DEFAULT_TOLERANCE);

    assertTrue(
        regressions.isEmpty(),
        regressions.size()
            + " regression(s) detected:\n"
            + regressions.stream().map(Regression::toString).toList());
  }
}
