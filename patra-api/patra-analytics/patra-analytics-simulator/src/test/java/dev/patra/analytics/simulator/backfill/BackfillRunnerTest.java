package dev.patra.analytics.simulator.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.analytics.simulator.event.BeijingDays;
import dev.patra.analytics.simulator.truth.RunSummary;
import dev.patra.analytics.simulator.truth.TruthFile;
import dev.patra.analytics.simulator.truth.TruthRow;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

class BackfillRunnerTest {

  @TempDir Path temp;

  private RunSummary run(BackfillParams params, BackfillFixture.RecordingSink sink, Path out) {
    return BackfillRunner.create(
            params,
            BackfillFixture.MODEL,
            BackfillFixture.POOL,
            sink,
            out,
            BackfillFixture.MAPPER,
            millis -> {})
        .run();
  }

  @Test
  void each_insert_holds_a_single_beijing_day_and_tokens_name_it() {
    BackfillFixture.RecordingSink sink = new BackfillFixture.RecordingSink();
    run(BackfillFixture.params(), sink, temp);

    for (int i = 0; i < sink.bodies.size(); i++) {
      Set<LocalDate> days =
          sink.bodies.get(i).stream().map(BackfillRunnerTest::day).collect(Collectors.toSet());
      assertThat(days).hasSize(1);
      String date = days.iterator().next().toString().replace("-", "");
      assertThat(sink.tokens.get(i))
          .startsWith("backfill:3:" + BackfillFixture.params().fingerprint() + ":" + date + ":");
    }
  }

  @Test
  void delivered_events_close_to_requested_and_summary_written() {
    BackfillFixture.RecordingSink sink = new BackfillFixture.RecordingSink();
    RunSummary summary = run(BackfillFixture.params(), sink, temp);

    long rows = sink.bodies.stream().mapToLong(List::size).sum();
    assertThat(rows).isEqualTo(summary.deliveredEvents() + summary.resentEvents());
    assertThat(summary.deliveredEvents()).isBetween(54_000L, 66_000L);
    assertThat(summary.resentEvents() / (double) summary.deliveredEvents()).isBetween(0.007, 0.013);
    assertThat(summary.eventsByDay()).hasSize(3);
    assertThat(RunSummary.read(temp.resolve("summary.json"), BackfillFixture.MAPPER))
        .isEqualTo(summary);
    assertThat(TruthFile.read(temp.resolve("truth.csv.gz"))).isNotEmpty();
  }

  @Test
  void resent_copy_lands_in_a_later_insert_than_original() {
    BackfillFixture.RecordingSink sink = new BackfillFixture.RecordingSink();
    run(BackfillFixture.params(), sink, temp);

    Map<String, Integer> firstSeen = new HashMap<>();
    int copies = 0;
    for (int i = 0; i < sink.bodies.size(); i++) {
      Set<String> inThisInsert = new HashSet<>();
      for (String line : sink.bodies.get(i)) {
        String id = eventId(line);
        assertThat(inThisInsert.add(id)).as("同一次写入里重复了 %s", id).isTrue();
        Integer earlier = firstSeen.putIfAbsent(id, i);
        if (earlier != null) {
          assertThat(earlier).isLessThan(i);
          copies++;
        }
      }
    }
    assertThat(copies).isPositive();
  }

  @Test
  void spill_written_as_next_day_with_spill_token() {
    BackfillFixture.RecordingSink sink = new BackfillFixture.RecordingSink();
    run(BackfillFixture.params(), sink, temp);

    List<Integer> spills =
        IntStream.range(0, sink.tokens.size())
            .filter(i -> sink.tokens.get(i).endsWith(":spill"))
            .boxed()
            .toList();
    assertThat(spills).isNotEmpty();
    for (int i : spills) {
      LocalDate tokenDay =
          LocalDate.parse(sink.tokens.get(i).split(":")[3], DateTimeFormatter.BASIC_ISO_DATE);
      assertThat(sink.bodies.get(i)).allMatch(line -> day(line).equals(tokenDay));
    }
  }

  @Test
  void rerun_sends_identical_tokens_and_bodies() {
    BackfillFixture.RecordingSink first = new BackfillFixture.RecordingSink();
    BackfillFixture.RecordingSink second = new BackfillFixture.RecordingSink();
    run(BackfillFixture.params(), first, temp.resolve("a"));
    run(BackfillFixture.params(), second, temp.resolve("b"));

    assertThat(second.tokens).isEqualTo(first.tokens);
    assertThat(second.bodies).isEqualTo(first.bodies);
  }

  @Test
  void only_days_writes_first_days_and_matches_full_run_prefix() {
    BackfillParams base = BackfillFixture.params();
    BackfillParams firstDay =
        new BackfillParams(
            3, 42L, 60_000L, 3, LocalDate.of(2026, 11, 1), 1, null, BackfillFixture.ARTICLES, null);
    BackfillFixture.RecordingSink full = new BackfillFixture.RecordingSink();
    BackfillFixture.RecordingSink partial = new BackfillFixture.RecordingSink();
    run(base, full, temp.resolve("full"));
    RunSummary summary = run(firstDay, partial, temp.resolve("partial"));

    assertThat(partial.tokens).isNotEmpty();
    assertThat(full.tokens.subList(0, partial.tokens.size())).isEqualTo(partial.tokens);
    assertThat(full.bodies.subList(0, partial.bodies.size())).isEqualTo(partial.bodies);
    assertThat(summary.eventsByDay()).hasSize(1);
    assertThat(TruthFile.read(temp.resolve("partial").resolve("truth.csv.gz")))
        .extracting(TruthRow::day)
        .containsOnly(LocalDate.of(2026, 10, 30));
  }

  @Test
  void from_day_skips_writes_but_truth_covers_all_days() {
    BackfillParams resume =
        new BackfillParams(
            3,
            42L,
            60_000L,
            3,
            LocalDate.of(2026, 11, 1),
            null,
            LocalDate.of(2026, 11, 1),
            BackfillFixture.ARTICLES,
            null);
    BackfillFixture.RecordingSink full = new BackfillFixture.RecordingSink();
    BackfillFixture.RecordingSink resumed = new BackfillFixture.RecordingSink();
    run(BackfillFixture.params(), full, temp.resolve("full"));
    run(resume, resumed, temp.resolve("resumed"));

    assertThat(resumed.tokens).allMatch(t -> t.contains(":20261101:"));
    assertThat(full.tokens).containsAll(resumed.tokens);
    assertThat(TruthFile.read(temp.resolve("resumed").resolve("truth.csv.gz")))
        .isEqualTo(TruthFile.read(temp.resolve("full").resolve("truth.csv.gz")));
  }

  @Test
  void retries_same_body_then_succeeds() {
    BackfillFixture.RecordingSink sink = new BackfillFixture.RecordingSink();
    sink.failuresLeft = 2;
    BackfillFixture.RecordingSink clean = new BackfillFixture.RecordingSink();
    run(BackfillFixture.params(), sink, temp.resolve("a"));
    run(BackfillFixture.params(), clean, temp.resolve("b"));

    assertThat(sink.calls).isEqualTo(clean.calls + 2);
    assertThat(sink.bodies).isEqualTo(clean.bodies);
  }

  @Test
  void disk_full_stops_immediately() {
    BackfillFixture.RecordingSink sink = new BackfillFixture.RecordingSink();
    sink.diskFull = true;

    assertThatThrownBy(() -> run(BackfillFixture.params(), sink, temp))
        .isInstanceOf(DiskFullException.class);
    assertThat(sink.calls).isEqualTo(1);
  }

  private static LocalDate day(String line) {
    JsonNode node = BackfillFixture.MAPPER.readTree(line);
    return BeijingDays.of(Instant.parse(node.get("derived_at").asString()));
  }

  private static String eventId(String line) {
    return BackfillFixture.MAPPER.readTree(line).get("event_id").asString();
  }
}
