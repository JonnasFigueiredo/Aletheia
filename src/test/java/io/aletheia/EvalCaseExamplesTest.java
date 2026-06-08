package io.aletheia;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.aletheia.client.MockLlmClient;
import io.aletheia.model.EvalCase;
import io.aletheia.runner.CaseResult;
import io.aletheia.runner.EvalCaseLoader;
import io.aletheia.runner.EvalRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Loads every YAML under {@code evalcases/} and runs each via the mock client. Happy-path cases
 * must pass. Known-fail cases in {@code evalcases/known-fails/} must fail, demonstrating that the
 * evaluators actually catch regressions.
 */
class EvalCaseExamplesTest {

  private static final Path EVALCASES_DIR = Path.of("evalcases");
  private static final Path KNOWN_FAILS_DIR = Path.of("evalcases", "known-fails");
  private static final String KNOWN_FAIL_ID = "groundedness-hallucinated-001";

  private static MockLlmClient client;

  @BeforeAll
  static void setUp() {
    client = new MockLlmClient();
  }

  @Test
  void happyPathCasesAllPass() throws IOException {
    assumeTrue(Files.isDirectory(EVALCASES_DIR), "evalcases/ directory not found");
    List<EvalCase> cases = EvalCaseLoader.loadFromDirectory(EVALCASES_DIR);
    assumeTrue(!cases.isEmpty(), "No eval cases found in evalcases/");

    for (EvalCase evalCase : cases) {
      CaseResult result = EvalRunner.runCase(evalCase, client);
      assertTrue(
          result.isPassed(),
          "Expected happy-path case '"
              + evalCase.getId()
              + "' to pass but it failed: "
              + result.getEvalResults().stream()
                  .filter(er -> !er.isPassed())
                  .map(er -> er.getEvaluatorType() + "=" + er.getMessage())
                  .toList());
    }
  }

  @Test
  void hallucinatedCaseFailsFaithfulness() throws IOException {
    assumeTrue(Files.isDirectory(KNOWN_FAILS_DIR), "evalcases/known-fails/ directory not found");
    List<EvalCase> knownFails = EvalCaseLoader.loadFromDirectory(KNOWN_FAILS_DIR);
    assumeTrue(!knownFails.isEmpty(), "No known-fail cases found in evalcases/known-fails/");

    EvalCase hallucinatedCase =
        knownFails.stream().filter(c -> KNOWN_FAIL_ID.equals(c.getId())).findFirst().orElse(null);
    assumeTrue(
        hallucinatedCase != null,
        "Expected to find known-fail case '" + KNOWN_FAIL_ID + "' but it was not present");

    CaseResult result = EvalRunner.runCase(hallucinatedCase, client);
    assertFalse(
        result.isPassed(),
        "Hallucinated case '"
            + KNOWN_FAIL_ID
            + "' should fail faithfulness evaluation but it passed — fixture may have changed");
  }
}
