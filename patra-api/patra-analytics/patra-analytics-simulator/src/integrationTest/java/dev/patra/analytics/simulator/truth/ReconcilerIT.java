package dev.patra.analytics.simulator.truth;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.client.api.Client;
import dev.patra.analytics.simulator.backfill.ClickHouseEventSink;
import dev.patra.analytics.simulator.event.BeijingDays;
import dev.patra.analytics.simulator.event.SyntheticIds;
import dev.patra.analytics.simulator.support.ClickHouseSimulatorFixture;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/// 对账查询的会话判定窗口：跨午夜的会话在次日才被标为爬虫，前一天也不能算（合并停掉，两次写入的会话特征各是一行）。
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
