package dev.patra.analytics.simulator.truth;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.client.api.Client;
import dev.patra.analytics.simulator.backfill.ClickHouseEventSink;
import dev.patra.analytics.simulator.event.BeijingDays;
import dev.patra.analytics.simulator.event.SyntheticIds;
import dev.patra.analytics.simulator.support.ClickHouseSimulatorFixture;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// 对账查询的会话判定窗口：跨午夜的会话在次日才被标为爬虫，前一天也不能算（合并停掉，两次写入的会话特征各是一行）；
/// 对账结果按天写进 `reconcile_results`，供运营分析面板画丢失率（面板 spec §6）。
class ReconcilerIT {

  private static final int RUN = 6;
  private static final LocalDate D = LocalDate.of(2026, 10, 20);

  private final Client client = ClickHouseSimulatorFixture.migratedDatabaseWithoutMerges();

  @AfterEach
  void close() {
    client.close();
  }

  @Test
  void session_labeled_bot_after_midnight_is_excluded_from_previous_day() {
    UUID crawler = UUID.randomUUID();
    UUID human = UUID.randomUUID();
    insert(
        "before-midnight",
        line(crawler, device("c"), 7, at(D, "23:59:00"), false),
        line(human, device("h"), 8, at(D, "23:59:10"), false));
    insert("after-midnight", line(crawler, device("c"), 7, at(D.plusDays(1), "00:00:30"), true));

    List<TruthRow> actual = new Reconciler(client, new RunScope(RUN)).actual(D);

    assertThat(actual).containsExactly(new TruthRow(D, 8, 1, 0));
  }

  @Test
  void writes_one_row_per_day(@TempDir Path dir) {
    insert(
        "two-days",
        line(UUID.randomUUID(), device("a"), 7, at(D, "10:00:00"), false),
        line(UUID.randomUUID(), device("b"), 7, at(D, "11:00:00"), false),
        line(UUID.randomUUID(), device("a"), 7, at(D.plusDays(1), "10:00:00"), false));
    Path truth = truthFile(dir, new TruthRow(D, 7, 2, 0), new TruthRow(D.plusDays(1), 7, 1, 0));
    Reconciler reconciler = new Reconciler(client, new RunScope(RUN));

    ReconcileResult result =
        reconciler.reconcile(
            truth, summary(Map.of(D.toString(), 2L, D.plusDays(1).toString(), 1L)));
    reconciler.writeDailyRows(reconciler.dailyRows());

    var rows =
        client.queryAll(
            "SELECT day, delivered, distinct_events, viewers_truth, viewers_ch, viewers_abs_diff"
                + " FROM reconcile_results FINAL WHERE run = "
                + RUN
                + " ORDER BY day");
    assertThat(rows).hasSize(2);
    assertThat(rows.getFirst().getLong("delivered")).isEqualTo(2);
    assertThat(rows.getFirst().getLong("distinct_events")).isEqualTo(2);
    assertThat(rows.getFirst().getLong("viewers_truth")).isEqualTo(2);
    assertThat(rows.getFirst().getLong("viewers_ch")).isEqualTo(2);
    assertThat(rows.getFirst().getLong("viewers_abs_diff")).isZero();
    assertThat(result.uniqueEvents()).isEqualTo(3);
  }

  @Test
  void skips_days_whose_partition_is_gone(@TempDir Path dir) {
    LocalDate expired = D.minusDays(5);
    insert("kept", line(UUID.randomUUID(), device("a"), 7, at(D, "10:00:00"), false));
    Path truth = truthFile(dir, new TruthRow(expired, 7, 1, 0), new TruthRow(D, 7, 1, 0));
    Reconciler reconciler = new Reconciler(client, new RunScope(RUN));

    ReconcileResult result =
        reconciler.reconcile(truth, summary(Map.of(expired.toString(), 4L, D.toString(), 1L)));

    assertThat(reconciler.dailyRows()).extracting(DailyReconcileRow::day).containsExactly(D);
    assertThat(result.deliveredEvents()).isEqualTo(1);
    assertThat(result.truthViewers()).isEqualTo(1);
  }

  @Test
  void skips_day_whose_partition_is_being_dropped_by_ttl(@TempDir Path dir) {
    insert("edge", line(UUID.randomUUID(), device("a"), 7, at(D, "10:00:00"), false));
    insert("kept", line(UUID.randomUUID(), device("a"), 7, at(D.plusDays(1), "10:00:00"), false));
    Path truth = truthFile(dir, new TruthRow(D, 7, 1, 0), new TruthRow(D.plusDays(1), 7, 1, 0));
    // 「今天」是 D 之后第 89 天：D 这一天的分区正在被 90 天 TTL 一片一片删掉，对出来的数不可信
    Clock clock =
        Clock.fixed(
            D.plusDays(89).atTime(12, 0).atZone(BeijingDays.ZONE).toInstant(), BeijingDays.ZONE);
    Reconciler reconciler = new Reconciler(client, new RunScope(RUN), clock);

    reconciler.reconcile(truth, summary(Map.of(D.toString(), 1L, D.plusDays(1).toString(), 1L)));

    assertThat(reconciler.dailyRows())
        .extracting(DailyReconcileRow::day)
        .containsExactly(D.plusDays(1));
  }

  private static Path truthFile(Path dir, TruthRow... rows) {
    Path file = dir.resolve("truth.csv.gz");
    try (TruthFile.Appender appender = TruthFile.open(file)) {
      appender.append(List.of(rows));
    }
    return file;
  }

  private static RunSummary summary(Map<String, Long> eventsByDay) {
    return RunSummary.builder()
        .mode("backfill")
        .run(RUN)
        .deliveredEvents(eventsByDay.values().stream().mapToLong(Long::longValue).sum())
        .eventsByDay(eventsByDay)
        .build();
  }

  private void insert(String token, String... lines) {
    new ClickHouseEventSink(client)
        .insert(() -> List.of(lines).iterator(), "it:" + token + ":" + UUID.randomUUID());
  }

  private static String device(String suffix) {
    String prefix = new SyntheticIds(RUN).devicePrefix() + suffix;
    return prefix + "A".repeat(43 - prefix.length());
  }

  private static Instant at(LocalDate day, String time) {
    return day.atTime(LocalTime.parse(time)).atZone(BeijingDays.ZONE).toInstant();
  }

  private static String line(
      UUID session, String device, long articleId, Instant time, boolean bot) {
    return """
        {"event_id":"%s","event_name":"article_viewed","schema_version":"1-0-0","source":"client",\
        "created_at":"%s","sent_at":"%s","received_at":"%s","derived_at":"%s",\
        "device_id":"%s","session_id":"%s","user_id":null,\
        "page_path":"/articles/%d","page_type":"article","referrer_host":null,"country_code":null,\
        "province":null,"browser_family":"Chrome","os_family":"Mac OS","device_class":"Desktop",\
        "is_bot":%s,"bot_reason":%s,"is_synthetic":true,"properties":{"article_id":"%d"}}"""
        .formatted(
            UUID.randomUUID(),
            time,
            time,
            time,
            time,
            device,
            session,
            articleId,
            bot,
            bot ? "\"rate\"" : "null",
            articleId);
  }
}
