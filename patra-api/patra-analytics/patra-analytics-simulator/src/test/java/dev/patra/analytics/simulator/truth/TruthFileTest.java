package dev.patra.analytics.simulator.truth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class TruthFileTest {

  @TempDir Path temp;

  @Test
  void truth_file_round_trips_across_appends() {
    Path file = temp.resolve("truth.csv.gz");
    TruthRow a = new TruthRow(LocalDate.of(2026, 11, 1), 39871245L, 12, 3);
    TruthRow b = new TruthRow(LocalDate.of(2026, 11, 2), 39871246L, 1, 0);

    try (TruthFile.Appender appender = TruthFile.open(file)) {
      appender.append(List.of(a));
      appender.append(List.of(b));
    }

    assertThat(TruthFile.read(file)).containsExactly(a, b);
  }

  @Test
  void reads_one_day_at_a_time_in_file_order() {
    Path file = temp.resolve("truth.csv.gz");
    TruthRow a = new TruthRow(LocalDate.of(2026, 11, 1), 1L, 1, 0);
    TruthRow b = new TruthRow(LocalDate.of(2026, 11, 1), 2L, 2, 0);
    TruthRow c = new TruthRow(LocalDate.of(2026, 11, 2), 1L, 3, 1);
    try (TruthFile.Appender appender = TruthFile.open(file)) {
      appender.append(List.of(a, b, c));
    }
    Map<LocalDate, List<TruthRow>> days = new LinkedHashMap<>();

    TruthFile.forEachDay(file, days::put);

    assertThat(days)
        .containsExactly(
            Map.entry(LocalDate.of(2026, 11, 1), List.of(a, b)),
            Map.entry(LocalDate.of(2026, 11, 2), List.of(c)));
  }

  @Test
  void summary_round_trips_and_tally_counts() {
    RunTally tally = new RunTally();
    tally.delivered("2026-11-01", 10);
    tally.delivered("2026-11-02", 5);
    tally.resent(2);
    tally.failed(1);
    tally.session("HUMAN");
    tally.session("HUMAN");
    tally.botEvent("rate");
    RunSummary summary = tally.summary("backfill", 7, 42, "ab12cd34");
    Path file = temp.resolve("summary.json");
    JsonMapper mapper = JsonMapper.builder().build();

    summary.write(file, mapper);

    RunSummary read = RunSummary.read(file, mapper);
    assertThat(read).isEqualTo(summary);
    assertThat(read.deliveredEvents()).isEqualTo(15);
    assertThat(read.eventsByDay()).containsEntry("2026-11-02", 5L);
    assertThat(read.sessionsByKind()).isEqualTo(Map.of("HUMAN", 2L));
    assertThat(read.botEventsByReason()).isEqualTo(Map.of("rate", 1L));
  }
}
