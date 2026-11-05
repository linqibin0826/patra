package dev.patra.analytics.simulator.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.simulator.SimulatorMain;
import dev.patra.analytics.simulator.live.RateStep;
import dev.patra.analytics.simulator.support.SmallArticlePoolFixture;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CommandLineTest {

  @TempDir Path temp;

  private final StringWriter out = new StringWriter();
  private final StringWriter err = new StringWriter();

  private int execute(String... args) {
    return SimulatorMain.commandLine()
        .setOut(new PrintWriter(out))
        .setErr(new PrintWriter(err))
        .execute(args);
  }

  @Test
  void help_lists_four_subcommands() {
    assertThat(execute("--help")).isZero();
    assertThat(out.toString()).contains("backfill", "live", "reconcile", "report");
  }

  @Test
  void rejects_run_out_of_range_with_exit_2() {
    int code =
        execute(
            "backfill",
            "--run",
            "900",
            "--events",
            "10",
            "--articles",
            SmallArticlePoolFixture.path().toString(),
            "--clickhouse-url",
            "http://127.0.0.1:1",
            "--out",
            temp.toString());

    assertThat(code).isEqualTo(2);
    assertThat(err.toString()).contains("1～899");
  }

  @Test
  void password_never_accepted_as_option() {
    int code =
        execute(
            "reconcile",
            "--run",
            "1",
            "--clickhouse-url",
            "http://127.0.0.1:1",
            "--clickhouse-password",
            "secret");

    assertThat(code).isEqualTo(2);
  }

  @Test
  void ramp_parses_into_steps() {
    CommandLine line = SimulatorMain.commandLine();
    line.parseArgs(
        "live",
        "--run",
        "2",
        "--ramp",
        "500,1000",
        "--step-length",
        "2m",
        "--articles",
        "a.csv",
        "--collector-url",
        "http://127.0.0.1:1/events");
    LiveCommand live = (LiveCommand) line.getSubcommands().get("live").getCommand();

    assertThat(live.steps())
        .containsExactly(
            new RateStep(500, Duration.ofMinutes(2)), new RateStep(1000, Duration.ofMinutes(2)));
  }

  @Test
  void single_rate_is_one_step_of_duration() {
    CommandLine line = SimulatorMain.commandLine();
    line.parseArgs(
        "live",
        "--run",
        "2",
        "--duration",
        "90s",
        "--articles",
        "a.csv",
        "--collector-url",
        "http://127.0.0.1:1/events");
    LiveCommand live = (LiveCommand) line.getSubcommands().get("live").getCommand();

    assertThat(live.steps()).containsExactly(new RateStep(1200, Duration.ofSeconds(90)));
  }

  @Test
  void params_json_written_without_password() throws Exception {
    CommandLine line = SimulatorMain.commandLine();
    line.parseArgs(
        "backfill",
        "--run",
        "7",
        "--seed",
        "9",
        "--events",
        "1000",
        "--days",
        "3",
        "--articles",
        "a.csv",
        "--clickhouse-url",
        "http://127.0.0.1:1",
        "--out",
        temp.toString());
    BackfillCommand backfill = (BackfillCommand) line.getSubcommands().get("backfill").getCommand();

    Path dir = OutputDirectory.prepare(temp, 7);
    OutputDirectory.writeParams(dir, backfill.params(), JsonMapper.builder().build());

    String json = Files.readString(dir.resolve("params.json"));
    assertThat(dir.getFileName().toString()).isEqualTo("run-007");
    assertThat(json).doesNotContainIgnoringCase("password");
    JsonNode params = JsonMapper.builder().build().readTree(json);
    assertThat(params.get("run").asInt()).isEqualTo(7);
    assertThat(params.get("events").asLong()).isEqualTo(1000);
    assertThat(params.get("days").asInt()).isEqualTo(3);
  }

  @Test
  void durations_accept_short_forms() throws Exception {
    DurationConverter converter = new DurationConverter();

    assertThat(converter.convert("30m")).isEqualTo(Duration.ofMinutes(30));
    assertThat(converter.convert("90s")).isEqualTo(Duration.ofSeconds(90));
    assertThat(converter.convert("1h")).isEqualTo(Duration.ofHours(1));
    assertThat(converter.convert("PT5M")).isEqualTo(Duration.ofMinutes(5));
  }
}
