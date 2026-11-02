package dev.patra.analytics.infra.ingest.store;

import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.MAPPER;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.event;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.migratedDatabase;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.number;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.pageViewIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clickhouse.client.api.Client;
import dev.patra.analytics.domain.ingest.exception.IngestRejectedException;
import dev.patra.analytics.domain.ingest.exception.IngestUnavailableException;
import dev.patra.analytics.infra.ingest.config.ClickHouseClientConfiguration;
import dev.patra.analytics.infra.ingest.config.ClickHouseProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

class ClickHouseEventStoreAdapterIT {

  private static final String DLQ_RECORD =
      """
      {"received_at":"2026-11-01T08:30:12.402Z","reason_code":"ingest_rejected",\
      "reason_detail":"Code: 27","event_name":"article_viewed",\
      "raw":"{\\"user_id\\":\\"5\\",\\"event_id\\":\\"x\\"}","raw_truncated":false}""";

  private final Client client = migratedDatabase();
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final ClickHouseEventStoreAdapter adapter =
      new ClickHouseEventStoreAdapter(client, meters);

  @AfterEach
  void close() {
    client.close();
  }

  @Test
  void inserts_events_with_token() {
    List<String> lines = List.of(json(event(node -> {})), json(event(node -> {})));

    adapter.insertEvents(lines, "T|p0:0-1");
    adapter.insertEvents(lines, "T|p0:0-1");

    assertThat(number(client, "SELECT count() AS n FROM events")).isEqualTo(2);
    assertThat(
            meters
                .get("analytics.ingest.insert")
                .tag("table", "events")
                .tag("outcome", "success")
                .timer()
                .count())
        .isEqualTo(2);
  }

  @Test
  void malformed_line_is_rejected() {
    assertThatThrownBy(() -> adapter.insertEvents(List.of("{\"event_id\":\"x\"}"), "T|p0:0-0"))
        .isInstanceOf(IngestRejectedException.class);
    assertThatThrownBy(() -> adapter.insertEvents(List.of("not json at all"), "T|p0:1-1"))
        .isInstanceOf(IngestRejectedException.class);
  }

  @Test
  void unreachable_server_is_unavailable() {
    try (Client unreachable =
        new ClickHouseClientConfiguration()
            .clickHouseClient(
                new ClickHouseProperties("http://127.0.0.1:1", "default", "", "default"))) {
      ClickHouseEventStoreAdapter down = new ClickHouseEventStoreAdapter(unreachable, meters);

      assertThatThrownBy(() -> down.insertEvents(List.of(json(event(node -> {}))), "T|p0:0-0"))
          .isInstanceOf(IngestUnavailableException.class);
    }
  }

  @Test
  void inserts_dlq_records() {
    adapter.insertDlq(List.of(DLQ_RECORD), "D|p0:0-0");

    assertThat(
            number(
                client,
                "SELECT count() AS n FROM dlq_events"
                    + " WHERE reason_code = 'ingest_rejected' AND user_id = 5"))
        .isEqualTo(1);
  }

  @Test
  void finds_users_after_cursor() {
    adapter.insertEvents(
        List.of(json(userEvent(3)), json(userEvent(5)), json(userEvent(9)), json(anonymous())),
        "T|p0:0-3");

    assertThat(adapter.findUsersAfter(4, 10)).containsExactly(5L, 9L);
    assertThat(adapter.findUsersAfter(0, 1)).containsExactly(3L);
  }

  @Test
  void skips_synthetic_user_id_range() {
    // 模拟器的合成用户（9e18 起）在 identity 里查不到，清理任务每天拿去查一遍是白费（PAP-171 spec §15）
    adapter.insertEvents(
        List.of(json(userEvent(5)), json(userEvent(9_000_000_000_070_000_001L))), "T|p0:0-1");

    assertThat(adapter.findUsersAfter(0, 10)).containsExactly(5L);
  }

  @Test
  void finds_users_left_only_in_article_session_reads() throws Exception {
    // 日活表 400 天后过期，文献 × 会话汇总长期保留：只看日活表会漏掉很久以前活跃过的用户
    adapter.insertEvents(List.of(json(userEvent(5)), json(userEvent(9))), "T|p0:0-1");
    client
        .execute("DELETE FROM daily_person_activity WHERE user_id = 5")
        .get(30, TimeUnit.SECONDS)
        .close();

    assertThat(adapter.findUsersAfter(0, 10)).containsExactly(5L, 9L);
    assertThat(adapter.findUsersAfter(5, 10)).containsExactly(9L);
  }

  @Test
  void deletes_users_everywhere() {
    ObjectNode five = userEvent(5);
    ObjectNode nine = userEvent(9);
    adapter.insertEvents(List.of(json(pageViewIn(five)), json(five), json(nine)), "T|p0:0-2");
    adapter.insertDlq(List.of(DLQ_RECORD), "D|p0:0-0");

    adapter.deleteUsers(List.of(5L, 7L));

    assertThat(number(client, "SELECT count() AS n FROM events WHERE user_id = 5")).isZero();
    assertThat(number(client, "SELECT count() AS n FROM dlq_events WHERE user_id = 5")).isZero();
    assertThat(
            number(
                client, "SELECT count() AS n FROM article_session_reads WHERE person_id = 'u:5'"))
        .isZero();
    assertThat(number(client, "SELECT count() AS n FROM daily_person_activity WHERE user_id = 5"))
        .isZero();
    assertThat(number(client, "SELECT count() AS n FROM events WHERE user_id = 9")).isEqualTo(1);
    assertThat(
            number(
                client, "SELECT count() AS n FROM article_session_reads WHERE person_id = 'u:9'"))
        .isEqualTo(1);
    assertThat(adapter.findUsersAfter(0, 10)).containsExactly(9L);
  }

  private static ObjectNode userEvent(long userId) {
    return event(node -> node.put("user_id", String.valueOf(userId)));
  }

  private static ObjectNode anonymous() {
    return event(node -> node.putNull("user_id"));
  }

  private static String json(ObjectNode node) {
    return MAPPER.writeValueAsString(node);
  }
}
