package dev.patra.analytics.infra.ads;

import static dev.patra.analytics.infra.ads.AdsITFixture.at;
import static dev.patra.analytics.infra.ads.AdsITFixture.backfill;
import static dev.patra.analytics.infra.ads.AdsITFixture.execute;
import static dev.patra.analytics.infra.ads.AdsITFixture.migrated;
import static dev.patra.analytics.infra.ads.AdsITFixture.rows;
import static dev.patra.analytics.infra.ads.AdsITFixture.utc;
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

/// 数据质量（运营分析面板 spec §4.5）：迟到分布（服务端收到时刻减客户端产生时刻）与重复率（写入行数对去重后条数）。
class AdsDailyQualityIT {

  private static final LocalDate D = LocalDate.of(2026, 10, 1);

  private static final String DEVICE = "Qty" + "x".repeat(40);

  private final Client client = migrated();

  @AfterEach
  void close() {
    client.close();
  }

  @Test
  void lateness_buckets() {
    insert(
        client,
        "T|late",
        List.of(
            late("2026-10-01 10:00:02", "2026-10-01 10:00:00"),
            late("2026-10-01 10:00:00", "2026-10-01 10:00:00"),
            late("2026-10-01 10:00:00", "2026-10-01 10:00:05"),
            late("2026-10-01 10:00:00", "2026-10-01 12:00:00")));

    backfill(client, "ads_daily_quality", D, D);

    List<GenericRecord> buckets =
        rows(
            client,
            "SELECT lateness_bucket, events FROM ads_daily_quality FINAL WHERE day = '%s'"
                    .formatted(D)
                + " ORDER BY lateness_bucket");
    assertThat(buckets)
        .extracting(row -> row.getString("lateness_bucket") + "=" + row.getLong("events"))
        .containsExactly("1s_10s=1", "clock_ahead=1", "ge_1h=1", "lt_1s=1");
  }

  @Test
  void duplicates_from_part_log() {
    ObjectNode twice = at("page_viewed", "client", uuid(), DEVICE, 0, "2026-10-01 10:00:00");
    insert(client, "T|first", List.of(twice, page("10:01:00")));
    insert(client, "T|second", List.of(twice, page("10:02:00")));
    execute(client, "SYSTEM FLUSH LOGS");

    backfill(client, "ads_daily_duplicates", D, D);

    GenericRecord row =
        rows(client, "SELECT written_rows, distinct_events FROM ads_daily_duplicates FINAL")
            .getFirst();
    assertThat(row.getLong("written_rows")).isEqualTo(4);
    assertThat(row.getLong("distinct_events")).isEqualTo(3);
  }

  @Test
  void day_missing_from_part_log_is_not_overwritten() {
    LocalDate old = LocalDate.of(2026, 9, 1);
    execute(
        client,
        "INSERT INTO ads_daily_duplicates (day, written_rows, distinct_events) VALUES ('%s', 100, 99)"
            .formatted(old));

    backfill(client, "ads_daily_duplicates", old, old);

    GenericRecord row =
        rows(client, "SELECT written_rows, distinct_events FROM ads_daily_duplicates FINAL")
            .getFirst();
    assertThat(row.getLong("written_rows")).isEqualTo(100);
    assertThat(row.getLong("distinct_events")).isEqualTo(99);
  }

  /// 一条浏览器事件：客户端在 `createdAt` 产生，服务端在 `receivedAt` 收到（北京时刻）。
  private static ObjectNode late(String createdAt, String receivedAt) {
    ObjectNode node = at("page_viewed", "client", uuid(), DEVICE, 0, receivedAt);
    node.put("created_at", utc(createdAt));
    return node;
  }

  private static ObjectNode page(String time) {
    return at("page_viewed", "client", uuid(), DEVICE, 0, "2026-10-01 " + time);
  }

  private static String uuid() {
    return UUID.randomUUID().toString();
  }
}
