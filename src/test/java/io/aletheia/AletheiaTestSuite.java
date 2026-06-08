package io.aletheia;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aletheia.client.LlmClient;
import io.aletheia.client.LlmClientFactory;
import io.aletheia.config.AletheiaConfig;
import io.aletheia.model.EvalCase;
import io.aletheia.report.HtmlReporter;
import io.aletheia.report.JsonReporter;
import io.aletheia.runner.CaseResult;
import io.aletheia.runner.EvalCaseLoader;
import io.aletheia.runner.EvalRunner;
import io.aletheia.runner.RunSummary;
import io.qameta.allure.Allure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

/**
 * Dynamic test suite that loads eval cases from {@code evalcases/} and runs each as a JUnit 5
 * {@link DynamicTest}.
 *
 * <p>Tag filtering: JUnit 5's {@code DynamicTest} does not natively support {@code @Tag}
 * annotations, so per-test tag filtering via {@code mvn test -Dgroups=smoke} is not possible at the
 * individual dynamic-test level. Instead, tests are named with their tags in the display name (e.g.
 * {@code "[smoke,regression] my-case-001"}) for visibility in reports. To filter by tag in
 * surefire, create a separate {@code @Tag("smoke") @TestFactory} method that filters the case list.
 *
 * <p>System properties:
 *
 * <ul>
 *   <li>{@code aletheia.cases.dir} — directory to load eval cases from (default: {@code evalcases})
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AletheiaTestSuite {

  private static final String CASES_DIR_PROP = "aletheia.cases.dir";
  private static final String DEFAULT_CASES_DIR = "evalcases";
  private static final ObjectMapper MAPPER =
      new ObjectMapper().writerWithDefaultPrettyPrinter().getConfig().getClass() == null
          ? new ObjectMapper()
          : new ObjectMapper();

  private final List<CaseResult> collectedResults = new CopyOnWriteArrayList<>();
  private LlmClient suiteClient;

  @BeforeAll
  void setUpClient() {
    AletheiaConfig cfg = AletheiaConfig.fromEnvironment();
    suiteClient = LlmClientFactory.fromConfig(cfg);
  }

  static List<EvalCase> loadCases() throws IOException {
    String dirProp = System.getProperty(CASES_DIR_PROP, DEFAULT_CASES_DIR);
    Path dir = Path.of(dirProp);
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    return EvalCaseLoader.loadFromDirectory(dir);
  }

  @TestFactory
  Stream<DynamicTest> aletheiaEvalCases() throws IOException {
    List<EvalCase> cases = loadCases();
    if (cases.isEmpty()) {
      return Stream.of(
          DynamicTest.dynamicTest(
              "no eval cases in " + System.getProperty(CASES_DIR_PROP, DEFAULT_CASES_DIR),
              () -> {}));
    }
    return cases.stream()
        .map(
            evalCase ->
                DynamicTest.dynamicTest(
                    displayName(evalCase),
                    () -> {
                      CaseResult result = EvalRunner.runCase(evalCase, suiteClient);
                      collectedResults.add(result);
                      attachToAllure(evalCase, result);
                      assertTrue(
                          result.isPassed(),
                          "Case '"
                              + evalCase.getId()
                              + "' failed: "
                              + result.getEvalResults().stream()
                                  .filter(er -> !er.isPassed())
                                  .map(er -> er.getEvaluatorType() + "=" + er.getMessage())
                                  .toList());
                    }));
  }

  @AfterAll
  void writeReports() throws IOException {
    AletheiaConfig cfg = AletheiaConfig.fromEnvironment();
    RunSummary summary =
        RunSummary.of(
            suiteClient.providerId(),
            cfg.getModel() != null ? cfg.getModel() : "unknown",
            new ArrayList<>(collectedResults));

    Path outDir = Path.of("target", "aletheia");
    Files.createDirectories(outDir);
    JsonReporter.write(summary, outDir.resolve("summary.json"));
    HtmlReporter.write(summary, outDir.resolve("summary.html"));
  }

  private static String displayName(EvalCase evalCase) {
    List<String> tags = evalCase.getTags();
    if (tags.isEmpty()) {
      return evalCase.getId();
    }
    return "[" + String.join(",", tags) + "] " + evalCase.getId();
  }

  private static void attachToAllure(EvalCase evalCase, CaseResult result) {
    Allure.addAttachment("prompt", "text/plain", evalCase.getPrompt());
    if (evalCase.getSystem() != null) {
      Allure.addAttachment("system", "text/plain", evalCase.getSystem());
    }
    if (evalCase.getContext() != null) {
      Allure.addAttachment("context", "text/plain", evalCase.getContext());
    }
    Allure.addAttachment("response", "text/plain", result.getResponse().getText());
    Allure.addAttachment("latencyMs", "text/plain", String.valueOf(result.getTotalLatencyMs()));
    Allure.addAttachment(
        "tokensIn", "text/plain", String.valueOf(result.getResponse().getTokensIn()));
    Allure.addAttachment(
        "tokensOut", "text/plain", String.valueOf(result.getResponse().getTokensOut()));
    try {
      ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
      String evalJson =
          mapper
              .writerWithDefaultPrettyPrinter()
              .writeValueAsString(
                  result.getEvalResults().stream()
                      .map(
                          er -> {
                            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                            m.put("type", er.getEvaluatorType());
                            m.put("passed", er.isPassed());
                            m.put("score", er.getScore());
                            m.put("message", er.getMessage());
                            return m;
                          })
                      .toList());
      Allure.addAttachment("evaluators", "application/json", evalJson);
    } catch (Exception e) {
      Allure.addAttachment("evaluators", "text/plain", result.getEvalResults().toString());
    }
  }
}
