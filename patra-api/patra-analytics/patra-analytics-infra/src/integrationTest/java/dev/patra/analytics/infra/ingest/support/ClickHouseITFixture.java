package dev.patra.analytics.infra.ingest.support;

import com.clickhouse.client.api.Client;
import com.clickhouse.data.ClickHouseFormat;
import dev.patra.analytics.infra.ingest.config.ClickHouseClientConfiguration;
import dev.patra.analytics.infra.ingest.config.ClickHouseProperties;
import dev.patra.tool.test.container.initializer.ClickHouseContainerInitializer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.testcontainers.clickhouse.ClickHouseContainer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/// ClickHouse 集成测试的夹具：每个用例一个新库，造富化事件行，按入库的设置写入。
public final class ClickHouseITFixture {

  /// 解析与生成 JSON
  public static final JsonMapper MAPPER = JsonMapper.builder().build();

  /// 富化事件样例里的文献 ID
  public static final long ARTICLE_ID = 39871245L;

  /// 埋点方案 §6 的「看过人数」查询（入库设计 §5.4），只数样例里那篇文献
  public static final String VIEWERS_SQL =
      """
      SELECT article_id, uniqExact(person_id) AS viewers
      FROM article_session_reads
      WHERE article_id IN (39871245) AND is_synthetic = 0 AND viewed = 1
        AND session_id IN (
            SELECT session_id FROM session_quality
            WHERE session_id IN (SELECT session_id FROM article_session_reads WHERE article_id IN (39871245))
            GROUP BY session_id HAVING max(has_client) = 1 AND max(has_bot) = 0)
      GROUP BY article_id
      """;

  private ClickHouseITFixture() {}

  /// 建一个新库，返回默认库指向它的客户端（还没迁移）。
  ///
  /// @return 客户端
  public static Client newDatabase() {
    ClickHouseContainer container = ClickHouseContainerInitializer.getClickHouseContainer();
    String url = "http://" + container.getHost() + ":" + container.getMappedPort(8123);
    String database = "it_" + UUID.randomUUID().toString().replace("-", "");
    ClickHouseClientConfiguration configuration = new ClickHouseClientConfiguration();
    try (Client admin =
        configuration.clickHouseClient(
            new ClickHouseProperties(
                url, container.getUsername(), container.getPassword(), "default"))) {
      admin.execute("CREATE DATABASE " + database).get(30, TimeUnit.SECONDS).close();
    } catch (Exception e) {
      throw new IllegalStateException("建测试库失败", e);
    }
    return configuration.clickHouseClient(
        new ClickHouseProperties(url, container.getUsername(), container.getPassword(), database));
  }

  /// 建一个新库并跑完迁移。
  ///
  /// @return 客户端
  public static Client migratedDatabase() {
    Client client = newDatabase();
    new ClickHouseMigrator(client).migrate();
    return client;
  }

  /// 读 PAP-164 的富化事件样例（服务端的文献浏览），换上新的 event_id，再按需改字段。
  ///
  /// @param changes 对样例的改动
  /// @return 一行富化事件
  public static ObjectNode event(Consumer<ObjectNode> changes) {
    try (InputStream in =
        ClickHouseITFixture.class
            .getClassLoader()
            .getResourceAsStream(
                "analytics/examples/valid/enriched-event--server-article-viewed.json")) {
      ObjectNode node =
          (ObjectNode) MAPPER.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
      node.put("event_id", UUID.randomUUID().toString());
      changes.accept(node);
      return node;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /// 同一会话里浏览器发的页面浏览：让会话算作有浏览器事件。
  ///
  /// @param sample 取它的会话、设备、用户
  /// @return 一行富化事件
  public static ObjectNode pageViewIn(ObjectNode sample) {
    return event(
        node -> {
          node.put("event_name", "page_viewed");
          node.put("source", "client");
          node.putObject("properties");
          node.set("session_id", sample.get("session_id"));
          node.set("device_id", sample.get("device_id"));
          node.set("user_id", sample.get("user_id"));
        });
  }

  /// 以 JSONEachRow 带令牌写进 `events`，用入库的写入设置。
  ///
  /// @param client 客户端
  /// @param token 去重令牌
  /// @param rows 行
  public static void insert(Client client, String token, List<ObjectNode> rows) {
    StringBuilder body = new StringBuilder();
    rows.forEach(row -> body.append(MAPPER.writeValueAsString(row)).append('\n'));
    try {
      client
          .insert(
              "events",
              new ByteArrayInputStream(body.toString().getBytes(StandardCharsets.UTF_8)),
              ClickHouseFormat.JSONEachRow,
              ClickHouseInserts.settings(token))
          .get(30, TimeUnit.SECONDS)
          .close();
    } catch (Exception e) {
      throw new IllegalStateException("写入失败", e);
    }
  }

  /// 执行一条只返回一个数的查询（列名 `n`）。
  ///
  /// @param client 客户端
  /// @param sql 查询
  /// @return 结果
  public static long number(Client client, String sql) {
    return client.queryAll(sql).getFirst().getLong("n");
  }

  /// 执行一条只返回一个字符串的查询（列名 `s`）。
  ///
  /// @param client 客户端
  /// @param sql 查询
  /// @return 结果
  public static String text(Client client, String sql) {
    return client.queryAll(sql).getFirst().getString("s");
  }

  /// 执行查询，取每行第一列的字符串。
  ///
  /// @param client 客户端
  /// @param sql 查询
  /// @return 各行的值
  public static List<String> texts(Client client, String sql) {
    return client.queryAll(sql).stream().map(row -> row.getString(1)).toList();
  }
}
