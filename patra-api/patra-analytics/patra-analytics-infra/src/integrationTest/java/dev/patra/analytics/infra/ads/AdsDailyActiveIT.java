package dev.patra.analytics.infra.ads;

import static dev.patra.analytics.infra.ads.AdsITFixture.at;
import static dev.patra.analytics.infra.ads.AdsITFixture.backfill;
import static dev.patra.analytics.infra.ads.AdsITFixture.migrated;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.insert;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.number;
import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.client.api.Client;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/// 活跃（运营分析面板 spec §4.1）：DAU / WAU / MAU 按滚动窗口，分真人与全部流量、合成与真实。
///
/// 10-01～10-03 三天：A 每天来；B 只 10-01 来；10-03 另有爬虫 C、不跑脚本的 D、合成的 S。
class AdsDailyActiveIT {

  private static final LocalDate D1 = LocalDate.of(2026, 10, 1);

  private static final LocalDate D3 = LocalDate.of(2026, 10, 3);

  private final Client client = migrated();

  @BeforeEach
  void seed() {
    List<ObjectNode> rows = new ArrayList<>();
    for (String day : List.of("2026-10-01", "2026-10-02", "2026-10-03")) {
      rows.add(at("page_viewed", "client", uuid(), device("A"), 0, day + " 10:00:00"));
    }
    rows.add(at("page_viewed", "client", uuid(), device("B"), 0, "2026-10-01 11:00:00"));
    ObjectNode bot = at("article_viewed", "server", uuid(), device("C"), 0, "2026-10-03 12:00:00");
    bot.put("is_bot", true);
    bot.put("bot_reason", "ua_list");
    rows.add(bot);
    rows.add(at("article_viewed", "server", uuid(), device("D"), 0, "2026-10-03 13:00:00"));
    ObjectNode synthetic =
        at("page_viewed", "client", uuid(), "sim001" + "s".repeat(37), 0, "2026-10-03 14:00:00");
    synthetic.put("is_synthetic", true);
    rows.add(synthetic);
    insert(client, "T|active", rows);
  }

  @AfterEach
  void close() {
    client.close();
  }

  @Test
  void dau_wau_mau_count_humans() {
    backfill(client, "ads_daily_active", D1, D3);

    assertThat(value("dau", "human", false)).isEqualTo(1);
    assertThat(value("wau", "human", false)).isEqualTo(2);
    assertThat(value("mau", "human", false)).isEqualTo(2);
  }

  @Test
  void all_traffic_includes_bots_and_no_script() {
    backfill(client, "ads_daily_active", D1, D3);

    assertThat(value("dau", "all", false)).isEqualTo(3);
    assertThat(value("wau", "all", false)).isEqualTo(4);
  }

  @Test
  void synthetic_people_are_separate_rows() {
    backfill(client, "ads_daily_active", D1, D3);

    assertThat(value("dau", "human", true)).isEqualTo(1);
    assertThat(value("mau", "human", true)).isEqualTo(1);
  }

  @Test
  void recomputing_a_day_does_not_double_count() {
    backfill(client, "ads_daily_active", D3, D3);
    backfill(client, "ads_daily_active", D3, D3);

    assertThat(value("dau", "human", false)).isEqualTo(1);
    assertThat(value("wau", "human", false)).isEqualTo(2);
  }

  private long value(String column, String traffic, boolean synthetic) {
    return number(
        client,
        "SELECT %s AS n FROM ads_daily_active FINAL WHERE day = '%s' AND traffic = '%s' AND is_synthetic = %s"
            .formatted(column, D3, traffic, synthetic));
  }

  private static String device(String name) {
    return name + "x".repeat(43 - name.length());
  }

  private static String uuid() {
    return UUID.randomUUID().toString();
  }
}
