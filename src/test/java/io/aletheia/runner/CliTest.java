package io.aletheia.runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTest {

  @TempDir Path tempDir;

  /** Baseline written by mock provider with no model set. Cleaned up around each test. */
  private static final Path MOCK_BASELINE = Path.of("baselines/mock-unknown.json");

  @BeforeEach
  @AfterEach
  void cleanUpBaseline() throws IOException {
    Files.deleteIfExists(MOCK_BASELINE);
  }

  @Test
  void recordBaseline_withEmptyCasesDir_returnsZero() throws IOException {
    Path casesDir = tempDir.resolve("evalcases");
    Files.createDirectories(casesDir);

    int code = Cli.run(new String[] {"record-baseline", "--cases", casesDir.toString()});
    assertThat(code).isEqualTo(0);
  }

  @Test
  void compareBaseline_withNoBaselineFile_returnsOne() throws IOException {
    Path casesDir = tempDir.resolve("evalcases");
    Files.createDirectories(casesDir);

    int code = Cli.run(new String[] {"compare-baseline", "--cases", casesDir.toString()});
    assertThat(code).isEqualTo(1);
  }

  @Test
  void unknownSubcommand_returnsOne() throws IOException {
    int code = Cli.run(new String[] {"unknown-command"});
    assertThat(code).isEqualTo(1);
  }

  @Test
  void noArgs_returnsOne() throws IOException {
    int code = Cli.run(new String[] {});
    assertThat(code).isEqualTo(1);
  }

  @Test
  void recordThenCompare_noRegressions_returnsZero() throws IOException {
    Path casesDir = tempDir.resolve("evalcases");
    Files.createDirectories(casesDir);

    int recordCode = Cli.run(new String[] {"record-baseline", "--cases", casesDir.toString()});
    assertThat(recordCode).isEqualTo(0);

    int compareCode = Cli.run(new String[] {"compare-baseline", "--cases", casesDir.toString()});
    assertThat(compareCode).isEqualTo(0);
  }
}
