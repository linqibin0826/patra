package dev.patra.analytics.infra.ads;

import static dev.patra.analytics.infra.ads.AdsITFixture.at;
import static dev.patra.analytics.infra.ads.AdsITFixture.execute;
import static dev.patra.analytics.infra.ads.AdsITFixture.migrated;
import static dev.patra.analytics.infra.ads.AdsITFixture.rows;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.insert;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.number;
import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/// 刷新视图本身能跑（运营分析面板 spec §3.2）：每张 ADS 表的 `*_rmv` 手动触发一次，没有报错、今天的数写进了目标表。
class AdsRefreshViewsIT {

  private static final List<String> TABLES =
      List.of(
          "ads_daily_active",
          "ads_person_first_day",
          "ads_cohort_retention",
          "ads_daily_sessions",
          "ads_daily_search",
          "ads_daily_zero_result_terms",
          "ads_daily_counter_items",
          "ads_daily_quality",
          "ads_daily_duplicates");

  private final Client client = migrated();

  @AfterEach
  void close() {
    client.close();
  }

  @Test
  void every_refresh_view_runs_and_writes_today() {
    seedToday();

    for (String table : TABLES) {
      execute(client, "SYSTEM REFRESH VIEW " + table + "_rmv");
      execute(client, "SYSTEM WAIT VIEW " + table + "_rmv");
    }

    List<GenericRecord> refreshes =
        rows(
            client,
            "SELECT view, exception FROM system.view_refreshes WHERE database = currentDatabase()");
    assertThat(refreshes).hasSize(TABLES.size());
    assertThat(refreshes).allSatisfy(row -> assertThat(row.getString("exception")).isEmpty());
    for (String table : TABLES) {
      assertThat(number(client, "SELECT count() AS n FROM " + table))
          .as("%s 有今天的数", table)
          .isPositive();
    }
  }

  /// 今天（北京时间）一个真人会话：访问、无结果检索、点结果、看文献、读全文。
  private void seedToday() {
    String day = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
    String session = UUID.randomUUID().toString();
    String device = "Rmv" + "x".repeat(40);
    ObjectNode search = at("search_performed", "server", session, device, 42, day + " 00:00:01");
    search
        .putObject("properties")
        .put("search_id", "S1")
        .put("mode", "keyword")
        .put("query", "nothing here")
        .put("result_count", 0);
    ObjectNode click = at("article_clicked", "client", session, device, 42, day + " 00:00:03");
    click.putObject("properties").put("list_type", "search").put("list_id", "S1");
    ObjectNode view = at("article_viewed", "server", session, device, 42, day + " 00:00:04");
    view.putObject("properties").put("article_id", "1001");
    ObjectNode read = at("fulltext_read", "client", session, device, 42, day + " 00:00:30");
    read.putObject("properties").put("article_id", "1001").put("visible_seconds", 10);
    insert(
        client,
        "T|rmv",
        List.of(
            at("page_viewed", "client", session, device, 42, day + " 00:00:02"),
            search,
            click,
            view,
            read));
    execute(client, "SYSTEM FLUSH LOGS");
  }
}
