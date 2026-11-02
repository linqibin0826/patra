package dev.patra.analytics.infra.ingest.support;

import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.ARTICLE_ID;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.VIEWERS_SQL;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.event;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.insert;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.migratedDatabase;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.number;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.pageViewIn;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.text;
import static dev.patra.analytics.infra.ingest.support.ClickHouseITFixture.texts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clickhouse.client.api.Client;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/// 入库设计 §5 的表结构与物化视图：计算列、北京时间的「天」、批次去重令牌、真人会话的判定。
class ClickHouseSchemaIT {

  private static final List<String> TABLES =
      List.of("events", "article_session_reads", "session_quality", "daily_person_activity");

  private final Client client = migratedDatabase();

  @AfterEach
  void close() {
    client.close();
  }

  @Test
  void computed_columns_are_filled() {
    ObjectNode loggedIn = event(node -> {});
    ObjectNode anonymous = event(node -> node.putNull("user_id"));

    insert(client, "T|p0:0-1", List.of(loggedIn, anonymous));

    assertThat(number(client, "SELECT count() AS n FROM events WHERE article_id = " + ARTICLE_ID))
        .isEqualTo(2);
    assertThat(texts(client, "SELECT person_id FROM events ORDER BY person_id"))
        .containsExactly(
            "d:" + loggedIn.get("device_id").asString(), "u:" + loggedIn.get("user_id").asString());
  }

  @Test
  void day_is_beijing_date() {
    insert(
        client,
        "T|p0:0-0",
        List.of(event(node -> node.put("derived_at", "2026-11-01T16:30:00.000Z"))));

    assertThat(
            text(client, "SELECT toString(toDate(derived_at, 'Asia/Shanghai')) AS s FROM events"))
        .isEqualTo("2026-11-02");
    assertThat(text(client, "SELECT toString(day) AS s FROM daily_person_activity"))
        .isEqualTo("2026-11-02");
    assertThat(text(client, "SELECT toString(day) AS s FROM article_session_reads"))
        .isEqualTo("2026-11-02");
  }

  @Test
  void same_token_twice_writes_once_everywhere() {
    ObjectNode view = event(node -> {});
    List<ObjectNode> rows = List.of(pageViewIn(view), view);

    insert(client, "T|p0:0-1", rows);
    Map<String, Long> first = counts();
    insert(client, "T|p0:0-1", rows);

    assertThat(first.values()).allSatisfy(count -> assertThat(count).isPositive());
    assertThat(counts()).isEqualTo(first);
  }

  @Test
  void mv_failure_retry_fills_aggregates() throws Exception {
    List<ObjectNode> rows = List.of(event(node -> {}));
    execute("DROP TABLE article_session_reads");

    assertThatThrownBy(() -> insert(client, "T|p0:0-0", rows))
        .isInstanceOf(IllegalStateException.class);

    execute(createStatementOf("clickhouse/V3__article_session_reads.sql"));
    insert(client, "T|p0:0-0", rows);

    assertThat(counts())
        .containsEntry("events", 1L)
        .containsEntry("article_session_reads", 1L)
        .containsEntry("session_quality", 1L)
        .containsEntry("daily_person_activity", 1L);
  }

  @Test
  void new_topic_id_is_not_deduplicated() {
    insert(client, "A|p0:0-0", List.of(event(node -> {})));
    insert(client, "B|p0:0-0", List.of(event(node -> {})));

    assertThat(number(client, "SELECT count() AS n FROM events")).isEqualTo(2);
  }

  @Test
  void same_event_id_in_two_batches_counts_once() {
    ObjectNode view = event(node -> {});
    insert(client, "T|p0:0-0", List.of(pageViewIn(view)));

    insert(client, "T|p0:1-1", List.of(view));
    insert(client, "T|p0:2-2", List.of(view));

    assertThat(
            number(
                client,
                "SELECT uniqExact(event_id) AS n FROM events WHERE event_name = 'article_viewed'"))
        .isEqualTo(1);
    assertThat(viewers()).isEqualTo(1);
  }

  @Test
  void same_person_same_session_counts_one_reader() {
    ObjectNode view = event(node -> {});
    insert(
        client,
        "T|p0:0-3",
        List.of(pageViewIn(view), view, event(sameSessionAs(view)), event(sameSessionAs(view))));

    assertThat(
            number(client, "SELECT count() AS n FROM events WHERE event_name = 'article_viewed'"))
        .isEqualTo(3);
    assertThat(viewers()).isEqualTo(1);
  }

  @Test
  void bot_and_server_only_sessions_are_excluded() {
    ObjectNode human = anonymousInNewSession();
    ObjectNode serverOnly = anonymousInNewSession();
    ObjectNode botted = anonymousInNewSession();
    ObjectNode synthetic = anonymousInNewSession();
    synthetic.put("is_synthetic", true);
    ObjectNode botEvent = pageViewIn(botted);
    botEvent.put("is_bot", true);
    botEvent.put("bot_reason", "rate");
    ObjectNode syntheticPageView = pageViewIn(synthetic);
    syntheticPageView.put("is_synthetic", true);

    insert(
        client,
        "T|p0:0-7",
        List.of(
            pageViewIn(human),
            human,
            serverOnly,
            pageViewIn(botted),
            botEvent,
            botted,
            syntheticPageView,
            synthetic));

    assertThat(viewers()).isEqualTo(1);
    assertThat(
            number(client, "SELECT count() AS n FROM article_session_reads WHERE is_synthetic = 1"))
        .isEqualTo(1);
  }

  /// 匿名的文献浏览，会话与设备都是新的。
  private static ObjectNode anonymousInNewSession() {
    return event(
        node -> {
          node.putNull("user_id");
          node.put("session_id", UUID.randomUUID().toString());
          node.put("device_id", UUID.randomUUID().toString().replace("-", "") + "abcdefghijk");
        });
  }

  /// 改成和 `sample` 同一会话、同一个人。
  private static Consumer<ObjectNode> sameSessionAs(ObjectNode sample) {
    return node -> {
      node.set("session_id", sample.get("session_id"));
      node.set("device_id", sample.get("device_id"));
      node.set("user_id", sample.get("user_id"));
    };
  }

  /// 样例文献的看过人数；没有行时为 0。
  private long viewers() {
    return client.queryAll(VIEWERS_SQL).stream().mapToLong(row -> row.getLong("viewers")).sum();
  }

  /// 四张表各自的 `count()`（不去重，看的是实际行数）。
  private Map<String, Long> counts() {
    return TABLES.stream()
        .collect(
            Collectors.toMap(
                table -> table, table -> number(client, "SELECT count() AS n FROM " + table)));
  }

  /// 执行一条 DDL。
  private void execute(String sql) throws Exception {
    client.execute(sql).get(30, TimeUnit.SECONDS).close();
  }

  /// 迁移脚本里的第一条语句（建表）。
  private static String createStatementOf(String resource) throws IOException {
    try (InputStream in = ClickHouseSchemaIT.class.getClassLoader().getResourceAsStream(resource)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).split(";\n")[0];
    }
  }
}
