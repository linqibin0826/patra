package dev.patra.analytics.infra.ads;

import static dev.patra.analytics.infra.ads.AdsITFixture.at;
import static dev.patra.analytics.infra.ads.AdsITFixture.backfill;
import static dev.patra.analytics.infra.ads.AdsITFixture.migrated;
import static dev.patra.analytics.infra.ads.AdsITFixture.rows;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.insert;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.number;
import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/// 检索（运营分析面板 spec §4.4）：无结果率、检索点击率（有点击的检索 ÷ 检索数）、无结果词排行。
class AdsSearchIT {

  private static final LocalDate D = LocalDate.of(2026, 10, 1);

  private static final String DEVICE = "Srch" + "x".repeat(39);

  private final Client client = migrated();

  private final String session = UUID.randomUUID().toString();

  private final List<ObjectNode> events = new ArrayList<>();

  @AfterEach
  void close() {
    client.close();
  }

  @Test
  void zero_result_rate_parts() {
    search("S1", "keyword", "covid", 12, "10:00:00");
    search("S2", "keyword", "covid vaccine", 0, "10:01:00");
    search("S3", "keyword", "asthma", 5, "10:02:00");
    load();

    GenericRecord row = searchRow();
    assertThat(row.getLong("searches")).isEqualTo(3);
    assertThat(row.getLong("zero_result_searches")).isEqualTo(1);
  }

  @Test
  void clicked_search_needs_click_with_same_list_id() {
    search("S1", "keyword", "covid", 12, "10:00:00");
    click("S1", "10:00:20");
    search("S2", "keyword", "asthma", 5, "10:01:00");
    click("OTHER", "10:01:20");
    load();

    assertThat(searchRow().getLong("clicked_searches")).isEqualTo(1);
  }

  @Test
  void terms_are_normalized() {
    search("S1", "keyword", "  Covid  Vaccine ", 0, "10:00:00");
    search("S2", "keyword", "covid vaccine", 0, "10:01:00");
    load();

    assertThat(
            number(
                client,
                "SELECT searches AS n FROM ads_daily_zero_result_terms FINAL"
                    + " WHERE term = 'covid vaccine' AND traffic = 'human'"))
        .isEqualTo(2);
  }

  @Test
  void exact_lookup_and_empty_query_are_not_terms() {
    search("S1", "exact_pmid", "12345678", 0, "10:00:00");
    search("S2", "keyword", "", 0, "10:01:00");
    load();

    assertThat(number(client, "SELECT count() AS n FROM ads_daily_zero_result_terms")).isZero();
  }

  @Test
  void people_merge_across_days() {
    search("S1", "keyword", "rare disease x", 0, "10:00:00");
    events.add(
        withSearch(
            at(
                "search_performed",
                "server",
                UUID.randomUUID().toString(),
                DEVICE,
                0,
                "2026-10-02 10:00:00"),
            "S9",
            "keyword",
            "rare disease x",
            0));
    insert(client, "T|days", events);

    backfill(client, "ads_daily_zero_result_terms", D, D.plusDays(1));

    assertThat(
            number(
                client,
                "SELECT uniqExactMerge(people) AS n FROM ads_daily_zero_result_terms FINAL"
                    + " WHERE term = 'rare disease x' AND traffic = 'all'"))
        .isEqualTo(1);
  }

  /// 加一条页面浏览让会话算真人，写入并回填这一天的两张表。
  private void load() {
    events.add(at("page_viewed", "client", session, DEVICE, 0, D + " 09:59:00"));
    insert(client, "T|search", events);
    backfill(client, "ads_daily_search", D, D);
    backfill(client, "ads_daily_zero_result_terms", D, D);
  }

  private GenericRecord searchRow() {
    return rows(
            client,
            "SELECT searches, zero_result_searches, clicked_searches FROM ads_daily_search FINAL"
                + " WHERE day = '%s' AND traffic = 'human' AND mode = 'keyword'".formatted(D))
        .getFirst();
  }

  private void search(String searchId, String mode, String query, int results, String time) {
    events.add(
        withSearch(
            at("search_performed", "server", session, DEVICE, 0, D + " " + time),
            searchId,
            mode,
            query,
            results));
  }

  private void click(String listId, String time) {
    ObjectNode click = at("article_clicked", "client", session, DEVICE, 0, D + " " + time);
    click.putObject("properties").put("list_type", "search").put("list_id", listId);
    events.add(click);
  }

  private static ObjectNode withSearch(
      ObjectNode node, String searchId, String mode, String query, int results) {
    node.putObject("properties")
        .put("search_id", searchId)
        .put("mode", mode)
        .put("query", query)
        .put("result_count", results);
    return node;
  }
}
