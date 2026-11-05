package dev.patra.analytics.infra.ads;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import dev.patra.analytics.infra.ingest.support.ClickHouseITFixture;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.node.ObjectNode;

/// ADS 层集成测试的夹具（运营分析面板 spec §3）：按北京时刻造事件、按天调计算视图回填、读结果。
public final class AdsITFixture {

  private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

  private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private AdsITFixture() {}

  /// 建一个新库并跑完迁移。
  ///
  /// @return 客户端
  public static Client migrated() {
    return ClickHouseITFixture.migratedDatabase();
  }

  /// 造一条事件：三个时刻都设为同一个北京时刻，属性为空对象。
  ///
  /// @param eventName 事件名
  /// @param source `server` 或 `client`
  /// @param sessionId 会话 ID（UUID）
  /// @param deviceId 设备 ID
  /// @param userId 用户 ID，0 表示未登录
  /// @param beijingTime 北京时刻，形如 `2026-10-01 10:00:00`
  /// @return 一行富化事件
  public static ObjectNode at(
      String eventName,
      String source,
      String sessionId,
      String deviceId,
      long userId,
      String beijingTime) {
    String utc = utc(beijingTime);
    return ClickHouseITFixture.event(
        node -> {
          node.put("event_name", eventName);
          node.put("source", source);
          node.put("session_id", sessionId);
          node.put("device_id", deviceId);
          if (userId == 0) {
            node.putNull("user_id");
          } else {
            node.put("user_id", String.valueOf(userId));
          }
          node.putObject("properties");
          node.put("created_at", utc);
          node.put("sent_at", utc);
          node.put("received_at", utc);
          node.put("derived_at", utc);
        });
  }

  /// 北京时刻换成 UTC 的 ISO 字符串（毫秒）。
  ///
  /// @param beijingTime 北京时刻，形如 `2026-10-01 10:00:00`
  /// @return 形如 `2026-10-01T02:00:00.000Z`
  public static String utc(String beijingTime) {
    return LocalDateTime.parse(beijingTime, INPUT)
        .atZone(BEIJING)
        .toInstant()
        .toString()
        .replaceFirst("Z$", ".000Z")
        .replaceFirst("\\.(\\d{3})\\.000Z$", ".$1Z");
  }

  /// 调计算视图回填一段日期：`INSERT INTO t SELECT * FROM t_calc(from = …, to = …)`。
  ///
  /// @param client 客户端
  /// @param table 目标表
  /// @param from 起（含）
  /// @param to 止（含）
  public static void backfill(Client client, String table, LocalDate from, LocalDate to) {
    execute(
        client,
        "INSERT INTO %s SELECT * FROM %s_calc(from = '%s', to = '%s')"
            .formatted(table, table, from, to));
  }

  /// 执行一条语句。
  ///
  /// @param client 客户端
  /// @param sql 语句
  public static void execute(Client client, String sql) {
    try {
      client.execute(sql).get(60, TimeUnit.SECONDS).close();
    } catch (Exception e) {
      throw new IllegalStateException("执行失败: " + sql, e);
    }
  }

  /// 执行查询，取全部行。
  ///
  /// @param client 客户端
  /// @param sql 查询
  /// @return 行
  public static List<GenericRecord> rows(Client client, String sql) {
    return client.queryAll(sql);
  }
}
