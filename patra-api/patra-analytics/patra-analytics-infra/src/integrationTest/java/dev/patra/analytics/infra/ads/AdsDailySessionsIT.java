package dev.patra.analytics.infra.ads;

import static dev.patra.analytics.infra.ads.AdsITFixture.at;
import static dev.patra.analytics.infra.ads.AdsITFixture.backfill;
import static dev.patra.analytics.infra.ads.AdsITFixture.migrated;
import static dev.patra.analytics.infra.ads.AdsITFixture.rows;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.insert;
import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/// 漏斗、跳出与会话构成（运营分析面板 spec §4.3）：一个会话一行聚合，按会话首日汇总。
class AdsDailySessionsIT {

  private static final LocalDate D = LocalDate.of(2026, 10, 1);

  private static final String DEVICE = "Ses" + "x".repeat(40);

  private final Client client = migrated();

  private final String session = UUID.randomUUID().toString();

  @AfterEach
  void close() {
    client.close();
  }

  @Test
  void funnel_counts_furthest_ordered_step() {
    insert(
        client,
        "T|f",
        List.of(
            event("page_viewed", "client", "2026-10-01 10:00:00"),
            event("search_performed", "server", "2026-10-01 10:00:05"),
            searchClick("2026-10-01 10:00:10"),
            event("article_viewed", "server", "2026-10-01 10:00:11")));

    assertThat(onlySession(D).getInteger("funnel_level")).isEqualTo(4);
  }

  @Test
  void search_before_page_view_still_reaches_step_two() {
    ObjectNode search = event("search_performed", "server", "2026-10-01 10:00:00");
    ObjectNode page = event("page_viewed", "client", "2026-10-01 10:00:00");
    page.put("derived_at", AdsITFixture.utc("2026-10-01 10:00:00").replace(".000Z", ".300Z"));
    insert(client, "T|order", List.of(search, page));

    assertThat(onlySession(D).getInteger("funnel_level")).isEqualTo(2);
  }

  @Test
  void pdf_download_counts_as_fulltext() {
    insert(
        client,
        "T|pdf",
        List.of(
            event("page_viewed", "client", "2026-10-01 10:00:00"),
            event("search_performed", "server", "2026-10-01 10:00:05"),
            searchClick("2026-10-01 10:00:10"),
            event("article_viewed", "server", "2026-10-01 10:00:11"),
            event("pdf_downloaded", "server", "2026-10-01 10:01:00")));

    assertThat(onlySession(D).getInteger("funnel_level")).isEqualTo(5);
  }

  @Test
  void no_page_view_is_level_zero() {
    insert(client, "T|ns", List.of(event("article_viewed", "server", "2026-10-01 10:00:00")));

    GenericRecord row = onlySession(D);
    assertThat(row.getInteger("funnel_level")).isZero();
    assertThat(row.getString("session_kind")).isEqualTo("no_script");
  }

  @Test
  void single_page_session_is_bounce() {
    insert(client, "T|one", List.of(event("page_viewed", "client", "2026-10-01 10:00:00")));

    GenericRecord row = onlySession(D);
    assertThat(row.getBoolean("single_page")).isTrue();
    assertThat(row.getString("session_kind")).isEqualTo("human");
  }

  @Test
  void bot_session_is_bot_kind() {
    ObjectNode bot = event("article_viewed", "server", "2026-10-01 10:00:05");
    bot.put("is_bot", true);
    bot.put("bot_reason", "rate");
    insert(client, "T|bot", List.of(event("page_viewed", "client", "2026-10-01 10:00:00"), bot));

    assertThat(onlySession(D).getString("session_kind")).isEqualTo("bot");
  }

  @Test
  void session_crossing_midnight_counts_on_start_day() {
    insert(
        client,
        "T|mid",
        List.of(
            event("page_viewed", "client", "2026-10-01 23:50:00"),
            event("page_viewed", "client", "2026-10-02 00:10:00")));

    backfill(client, "ads_daily_sessions", D, D.plusDays(1));

    List<GenericRecord> all =
        rows(client, "SELECT day, sessions, single_page FROM ads_daily_sessions FINAL");
    assertThat(all).hasSize(1);
    assertThat(all.getFirst().getLocalDate("day")).isEqualTo(D);
    assertThat(all.getFirst().getLong("sessions")).isEqualTo(1);
    assertThat(all.getFirst().getBoolean("single_page")).isFalse();
  }

  @Test
  void recompute_after_session_grows_keeps_only_latest() {
    insert(client, "T|v1", List.of(event("page_viewed", "client", "2026-10-01 10:00:00")));
    backfill(client, "ads_daily_sessions", D, D);
    insert(
        client,
        "T|v2",
        List.of(
            event("page_viewed", "client", "2026-10-01 10:05:00"),
            event("search_performed", "server", "2026-10-01 10:05:05")));

    backfill(client, "ads_daily_sessions", D, D);

    List<GenericRecord> latest =
        rows(
            client,
            "SELECT funnel_level, single_page, sessions FROM ads_daily_sessions_latest"
                + " WHERE day = '%s'".formatted(D));
    assertThat(latest).hasSize(1);
    assertThat(latest.getFirst().getInteger("funnel_level")).isEqualTo(2);
    assertThat(latest.getFirst().getBoolean("single_page")).isFalse();
  }

  @Test
  void crossing_midnight_backfilled_day_by_day_counts_once() {
    insert(
        client,
        "T|mid2",
        List.of(
            event("page_viewed", "client", "2026-10-01 23:50:00"),
            event("page_viewed", "client", "2026-10-02 00:10:00")));

    backfill(client, "ads_daily_sessions", D, D);
    backfill(client, "ads_daily_sessions", D.plusDays(1), D.plusDays(1));

    assertThat(
            rows(
                client,
                "SELECT day FROM ads_daily_sessions_latest WHERE day = '%s'"
                    .formatted(D.plusDays(1))))
        .isEmpty();
  }

  /// 回填这一天，断言恰好一个会话一行，返回它。
  private GenericRecord onlySession(LocalDate day) {
    backfill(client, "ads_daily_sessions", day, day);
    List<GenericRecord> all =
        rows(
            client,
            "SELECT funnel_level, single_page, session_kind, sessions FROM ads_daily_sessions FINAL"
                + " WHERE day = '%s'".formatted(day));
    assertThat(all).hasSize(1);
    assertThat(all.getFirst().getLong("sessions")).isEqualTo(1);
    return all.getFirst();
  }

  private ObjectNode event(String name, String source, String beijingTime) {
    return at(name, source, session, DEVICE, 0, beijingTime);
  }

  private ObjectNode searchClick(String beijingTime) {
    ObjectNode click = event("article_clicked", "client", beijingTime);
    click.putObject("properties").put("list_type", "search").put("list_id", "L1");
    return click;
  }
}
