package dev.patra.analytics.infra.ads;

import static dev.patra.analytics.infra.ads.AdsITFixture.at;
import static dev.patra.analytics.infra.ads.AdsITFixture.backfill;
import static dev.patra.analytics.infra.ads.AdsITFixture.execute;
import static dev.patra.analytics.infra.ads.AdsITFixture.migrated;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.insert;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.number;
import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.client.api.Client;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/// 设备与账号的对应（运营分析面板 spec §4.6）：登录后的事件同时带设备与用户，据此把登录前的设备认回账号。
class DeviceUserLinksIT {

  private static final String DEVICE = "Dev1" + "x".repeat(39);

  private static final String LINKED_USER_SQL =
      "SELECT argMin(user_id, first_at) AS n FROM device_user_links WHERE device_id = '"
          + DEVICE
          + "'";

  private final Client client = migrated();

  @AfterEach
  void close() {
    client.close();
  }

  @Test
  void links_device_to_user_seen_on_it() {
    String session = UUID.randomUUID().toString();
    insert(
        client,
        "T|a",
        List.of(
            at("article_viewed", "server", session, DEVICE, 0, "2026-10-01 09:00:00"),
            at("article_viewed", "server", session, DEVICE, 42, "2026-10-01 09:05:00")));

    assertThat(number(client, LINKED_USER_SQL)).isEqualTo(42);
  }

  @Test
  void first_user_wins_when_device_is_shared() {
    insert(
        client,
        "T|b",
        List.of(
            at("article_viewed", "server", uuid(), DEVICE, 43, "2026-10-01 11:00:00"),
            at("article_viewed", "server", uuid(), DEVICE, 42, "2026-10-01 10:00:00")));

    assertThat(number(client, LINKED_USER_SQL)).isEqualTo(42);
  }

  @Test
  void calc_view_matches_mv_for_a_day() {
    insert(
        client,
        "T|c",
        List.of(
            at("article_viewed", "server", uuid(), DEVICE, 42, "2026-10-01 10:00:00"),
            at("article_viewed", "server", uuid(), DEVICE, 43, "2026-10-02 10:00:00")));
    execute(client, "TRUNCATE TABLE device_user_links");

    backfill(client, "device_user_links", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1));

    assertThat(number(client, LINKED_USER_SQL)).isEqualTo(42);
    assertThat(number(client, "SELECT count() AS n FROM device_user_links")).isEqualTo(1);
  }

  private static String uuid() {
    return UUID.randomUUID().toString();
  }
}
