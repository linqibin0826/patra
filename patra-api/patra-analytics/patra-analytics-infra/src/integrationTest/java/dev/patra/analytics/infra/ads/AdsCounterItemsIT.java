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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/// 文献使用（运营分析面板 spec §4.7，COUNTER 5.1）：Unique 按会话去重，Total 做 30 秒双击过滤。
class AdsCounterItemsIT {

  private static final LocalDate D = LocalDate.of(2026, 10, 1);

  private static final long ARTICLE = 1001L;

  private static final String DEVICE = "Ctr" + "x".repeat(40);

  private final Client client = migrated();

  private final List<ObjectNode> events = new ArrayList<>();

  @AfterEach
  void close() {
    client.close();
  }

  @Test
  void double_click_within_30s_counts_once() {
    String session = session();
    view("article_viewed", session, "10:01:00");
    view("article_viewed", session, "10:01:29");

    assertThat(counts().getLong("total_investigations")).isEqualTo(1);
  }

  @Test
  void clicks_35s_apart_count_twice() {
    String session = session();
    view("article_viewed", session, "10:01:00");
    view("article_viewed", session, "10:01:35");

    assertThat(counts().getLong("total_investigations")).isEqualTo(2);
  }

  @Test
  void unique_counts_once_per_session() {
    String first = session();
    view("article_viewed", first, "10:00:00");
    view("article_viewed", first, "10:05:00");
    view("article_viewed", first, "10:10:00");
    String second = session();
    view("article_viewed", second, "15:00:00");

    assertThat(counts().getLong("unique_investigations")).isEqualTo(2);
  }

  @Test
  void requests_are_fulltext_or_pdf() {
    String session = session();
    view("fulltext_read", session, "10:00:00");
    view("pdf_downloaded", session, "10:01:00");
    view("article_collected", session, "10:02:00");

    GenericRecord row = counts();
    assertThat(row.getLong("total_requests")).isEqualTo(2);
    assertThat(row.getLong("total_investigations")).isEqualTo(3);
    assertThat(row.getLong("unique_requests")).isEqualTo(1);
  }

  /// 写入、回填这一天，取真人口径下这篇文献的一行。
  private GenericRecord counts() {
    insert(client, "T|counter", events);
    backfill(client, "ads_daily_counter_items", D, D);
    return rows(
            client,
            "SELECT unique_investigations, unique_requests, total_investigations, total_requests"
                + " FROM ads_daily_counter_items FINAL"
                + " WHERE day = '%s' AND article_id = %d AND traffic = 'human' AND NOT is_synthetic"
                    .formatted(D, ARTICLE))
        .getFirst();
  }

  /// 新会话：带一条页面浏览，让它算真人会话。
  private String session() {
    String session = UUID.randomUUID().toString();
    events.add(at("page_viewed", "client", session, DEVICE, 0, D + " 09:00:00"));
    return session;
  }

  private void view(String name, String session, String time) {
    ObjectNode node = at(name, "server", session, DEVICE, 0, D + " " + time);
    node.putObject("properties").put("article_id", String.valueOf(ARTICLE));
    events.add(node);
  }
}
