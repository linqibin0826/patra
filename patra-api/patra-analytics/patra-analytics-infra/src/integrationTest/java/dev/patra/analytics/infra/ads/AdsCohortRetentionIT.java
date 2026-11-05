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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/// 留存与新增（运营分析面板 spec §4.2、§4.6）：经典 N 日留存，登录前后的同一台设备认回同一个人。
class AdsCohortRetentionIT {

  private static final LocalDate D = LocalDate.of(2026, 10, 1);

  private final Client client = migrated();

  @AfterEach
  void close() {
    client.close();
  }

  @Test
  void classic_day_n_retention() {
    List<ObjectNode> events = new ArrayList<>();
    for (String person : List.of("P1", "P2", "P3", "P4")) {
      events.add(visit(person, 0, D));
    }
    events.add(visit("P1", 0, D.plusDays(1)));
    events.add(visit("P2", 0, D.plusDays(1)));
    for (String person : List.of("P1", "P2", "P3")) {
      events.add(visit(person, 0, D.plusDays(3)));
    }
    events.add(visit("P4", 0, D.plusDays(7)));
    insert(client, "T|ret", events);

    retention(D, D);

    GenericRecord row = cohort(D);
    assertThat(row.getLong("cohort_size")).isEqualTo(4);
    assertThat(row.getLong("d1")).isEqualTo(2);
    assertThat(row.getLong("d7")).isEqualTo(1);
    assertThat(row.getLong("d30")).isZero();
  }

  @Test
  void stitched_device_is_not_new_again() {
    insert(
        client,
        "T|stitch",
        List.of(visit("X", 0, D), visit("X", 42, D.plusDays(1)), visit("X", 42, D.plusDays(2))));

    retention(D, D.plusDays(2));

    GenericRecord row = cohort(D);
    assertThat(row.getLong("cohort_size")).isEqualTo(1);
    assertThat(row.getLong("d1")).isEqualTo(1);
    assertThat(
            number(
                client,
                "SELECT count() AS n FROM ads_cohort_retention FINAL WHERE cohort_day > '%s'"
                    .formatted(D)))
        .isZero();
  }

  @Test
  void returning_person_is_not_new_even_if_first_seen_long_before_window() {
    insert(
        client,
        "T|old",
        List.of(
            visit("Old", 0, D.minusDays(60)),
            visit("Old", 0, D),
            visit("Dev2", 42, D.minusDays(40)),
            visit("Dev3", 0, D.minusDays(50)),
            visit("Dev3", 42, D.minusDays(45)),
            visit("Dev3", 0, D)));

    retention(D, D);

    assertThat(
            number(
                client,
                "SELECT count() AS n FROM ads_cohort_retention_latest WHERE cohort_day = '%s'"
                    .formatted(D)))
        .isZero();
  }

  @Test
  void future_day_n_is_null_not_zero() {
    LocalDate cohortDay = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(3);
    insert(client, "T|future", List.of(visit("F", 0, cohortDay)));

    retention(cohortDay, cohortDay);

    GenericRecord row = cohort(cohortDay);
    assertThat(row.getObject("d1")).isNotNull();
    assertThat(row.getObject("d7")).isNull();
    assertThat(row.getObject("d30")).isNull();
  }

  /// 先补每人首日（从很早到区间末），再算这段同期群；与回填脚本、刷新视图的先后一致。
  private void retention(LocalDate from, LocalDate to) {
    backfill(client, "ads_person_first_day", from.minusDays(120), to);
    backfill(client, "ads_cohort_retention", from, to);
  }

  private GenericRecord cohort(LocalDate day) {
    return rows(
            client,
            "SELECT cohort_size, d1, d7, d30 FROM ads_cohort_retention FINAL"
                + " WHERE cohort_day = '%s' AND traffic = 'human' AND NOT is_synthetic"
                    .formatted(day))
        .getFirst();
  }

  /// 一个人某天的一次访问（浏览器页面浏览），未登录时 `userId` 传 0。
  private static ObjectNode visit(String device, long userId, LocalDate day) {
    return at(
        "page_viewed",
        "client",
        UUID.randomUUID().toString(),
        device + "x".repeat(43 - device.length()),
        userId,
        day + " 10:00:00");
  }
}
