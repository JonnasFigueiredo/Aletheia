package io.aletheia.runner;

import io.aletheia.client.LlmClient;
import io.aletheia.client.LlmClientFactory;
import io.aletheia.config.AletheiaConfig;
import io.aletheia.model.EvalCase;
import io.aletheia.runner.baseline.BaselineComparer;
import io.aletheia.runner.baseline.BaselineSnapshot;
import io.aletheia.runner.baseline.BaselineStore;
import io.aletheia.runner.baseline.Regression;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI entry point with two subcommands.
 *
 * <ul>
 *   <li>{@code record-baseline [--provider mock] [--cases evalcases/]} — runs the suite and writes
 *       a baseline snapshot.
 *   <li>{@code compare-baseline [--tolerance 0.05]} — runs the suite, compares against the stored
 *       baseline, exits 1 on any regression.
 * </ul>
 */
public final class Cli {

  private Cli() {}

  public static void main(String[] args) throws IOException {
    System.exit(run(args));
  }

  /**
   * Executes the CLI and returns the exit code (0 = success, 1 = failure). Separated from {@code
   * main()} for testability.
   */
  static int run(String[] args) throws IOException {
    if (args.length == 0) {
      printUsage();
      return 1;
    }

    String subcommand = args[0];
    return switch (subcommand) {
      case "record-baseline" -> recordBaseline(args);
      case "compare-baseline" -> compareBaseline(args);
      default -> {
        System.err.println("Unknown subcommand: " + subcommand);
        printUsage();
        yield 1;
      }
    };
  }

  private static int recordBaseline(String[] args) throws IOException {
    String provider = argValue(args, "--provider", "mock");
    String casesDir = argValue(args, "--cases", "evalcases");

    AletheiaConfig cfg = buildConfig(provider);
    LlmClient client = LlmClientFactory.fromConfig(cfg);
    List<EvalCase> cases = EvalCaseLoader.loadFromDirectory(Path.of(casesDir));
    RunSummary summary = EvalRunner.runSuite(cases, client, cfg);

    Path baselinePath = BaselineStore.defaultPath(summary.getProvider(), summary.getModel());
    BaselineStore.save(baselinePath, summary);
    System.out.println("Baseline written to " + baselinePath);
    return 0;
  }

  private static int compareBaseline(String[] args) throws IOException {
    double tolerance = Double.parseDouble(argValue(args, "--tolerance", "0.05"));
    String provider = argValue(args, "--provider", "mock");
    String casesDir = argValue(args, "--cases", "evalcases");

    AletheiaConfig cfg = buildConfig(provider);
    LlmClient client = LlmClientFactory.fromConfig(cfg);
    List<EvalCase> cases = EvalCaseLoader.loadFromDirectory(Path.of(casesDir));
    RunSummary summary = EvalRunner.runSuite(cases, client, cfg);

    Path baselinePath = BaselineStore.defaultPath(summary.getProvider(), summary.getModel());
    if (!baselinePath.toFile().exists()) {
      System.err.println("No baseline found at " + baselinePath + ". Run record-baseline first.");
      return 1;
    }
    BaselineSnapshot baseline = BaselineStore.load(baselinePath);
    List<Regression> regressions = BaselineComparer.compare(summary, baseline, tolerance);

    if (regressions.isEmpty()) {
      System.out.println("No regressions detected.");
      return 0;
    } else {
      System.err.println(regressions.size() + " regression(s) detected:");
      regressions.forEach(r -> System.err.println("  " + r));
      return 1;
    }
  }

  private static AletheiaConfig buildConfig(String providerOverride) {
    AletheiaConfig base = AletheiaConfig.fromEnvironment();
    return AletheiaConfig.of(
        providerOverride,
        base.getBaseUrl(),
        base.getApiKey(),
        base.getModel(),
        base.getTemperature(),
        base.getEmbedModel(),
        base.getFixturePath(),
        base.getTimeoutSeconds());
  }

  private static String argValue(String[] args, String flag, String defaultValue) {
    for (int i = 0; i < args.length - 1; i++) {
      if (args[i].equals(flag)) {
        return args[i + 1];
      }
    }
    return defaultValue;
  }

  private static void printUsage() {
    System.err.println("Usage:");
    System.err.println("  record-baseline [--provider mock] [--cases evalcases/]");
    System.err.println(
        "  compare-baseline [--tolerance 0.05] [--provider mock] [--cases evalcases/]");
  }
}
