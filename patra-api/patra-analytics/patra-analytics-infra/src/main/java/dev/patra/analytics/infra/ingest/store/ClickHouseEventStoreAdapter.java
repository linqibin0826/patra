package dev.patra.analytics.infra.ingest.store;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.DataStreamWriter;
import com.clickhouse.data.ClickHouseFormat;
import dev.patra.analytics.domain.ingest.exception.IngestRejectedException;
import dev.patra.analytics.domain.ingest.exception.IngestUnavailableException;
import dev.patra.analytics.domain.ingest.port.store.EventStorePort;
import dev.patra.analytics.infra.ingest.support.ClickHouseInserts;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/// 事件仓库的 ClickHouse 实现：一批一次 `INSERT ... FORMAT JSONEachRow`，消息原样写入（入库设计 §4.2）。
@Component
public class ClickHouseEventStoreAdapter implements EventStorePort {

  /// 写入耗时指标（标签 `table`、`outcome`）
  static final String INSERT_TIMER = "analytics.ingest.insert";

  /// 一次写入或删除最多等多久；客户端的读超时也是 30 秒
  private static final long TIMEOUT_SECONDS = 60;

  /// 流量模拟器合成用户 ID 的起点（PAP-171 spec §4）：这之上的用户在 identity 里查不到，清理任务不去查
  private static final long SYNTHETIC_USER_ID_FLOOR = 9_000_000_000_000_000_000L;

  private final Client client;
  private final MeterRegistry meterRegistry;

  /// 创建适配器。
  ///
  /// @param client ClickHouse 客户端
  /// @param meterRegistry 指标
  public ClickHouseEventStoreAdapter(Client client, MeterRegistry meterRegistry) {
    this.client = client;
    this.meterRegistry = meterRegistry;
  }

  /// 整批写明细表。
  ///
  /// @param jsonLines 每条一行 JSON
  /// @param token 批次去重令牌
  @Override
  public void insertEvents(List<String> jsonLines, String token) {
    insert("events", jsonLines, token);
  }

  /// 整批写 dlq 表。
  ///
  /// @param jsonLines 每条一行 JSON
  /// @param token 批次去重令牌
  @Override
  public void insertDlq(List<String> jsonLines, String token) {
    insert("dlq_events", jsonLines, token);
  }

  /// 找游标之后的登录用户：日活表（明细只留 90 天，日活表留 400 天）并上文献 × 会话汇总（长期保留），
  /// 否则 400 天前活跃过的用户注销时，汇总里的记录永远找不到、删不掉。流量模拟器的合成用户不算（每天白查几万个）。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 最多几个
  /// @return 用户 ID，升序
  @Override
  public List<Long> findUsersAfter(long afterUserId, int limit) {
    return client
        .queryAll(
            """
            SELECT user_id FROM (
                SELECT toUInt64(user_id) AS user_id FROM daily_person_activity
                WHERE user_id > {after:UInt64} AND user_id < {synthetic:UInt64}
                UNION ALL
                SELECT toUInt64OrZero(substring(person_id, 3)) AS user_id FROM article_session_reads
                WHERE startsWith(person_id, 'u:') AND toUInt64OrZero(substring(person_id, 3)) > {after:UInt64}
                  AND toUInt64OrZero(substring(person_id, 3)) < {synthetic:UInt64}
            )
            GROUP BY user_id ORDER BY user_id LIMIT {limit:UInt32}
            """,
            Map.of("after", afterUserId, "limit", limit, "synthetic", SYNTHETIC_USER_ID_FLOOR))
        .stream()
        .map(row -> row.getLong("user_id"))
        .toList();
  }

  /// 一批用户一条语句地轻量删除：`events` 的排序键里没有 `user_id`，每条删除都扫全表，逐个用户删会扫上百遍。
  ///
  /// @param userIds 用户 ID
  @Override
  public void deleteUsers(List<Long> userIds) {
    if (userIds.isEmpty()) {
      return;
    }
    String ids = userIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
    String persons = userIds.stream().map(id -> "'u:" + id + "'").collect(Collectors.joining(", "));
    execute("DELETE FROM events WHERE user_id IN (" + ids + ")");
    execute("DELETE FROM dlq_events WHERE user_id IN (" + ids + ")");
    execute("DELETE FROM article_session_reads WHERE person_id IN (" + persons + ")");
    execute("DELETE FROM device_user_links WHERE user_id IN (" + ids + ")");
    execute("DELETE FROM ads_person_first_day WHERE person_id IN (" + persons + ")");
    // 日活表是找用户的唯一来源，最后删：中途失败时其他表的残留次日还能再找到
    execute("DELETE FROM daily_person_activity WHERE user_id IN (" + ids + ")");
  }

  /// 整批写入一张表，按错误码分成被拒与可重试两类，记耗时与成败。
  ///
  /// 逐行把 UTF-8 写进请求体，不先拼成整批的字符串与字节数组：满批 5 万条时那两份副本有一两百 MB，
  /// 容器内存上限只有 768 MB。客户端重试时会再调一次写出，消息还在内存里，照样写得出。
  ///
  /// @param table 表名
  /// @param jsonLines 每条一行 JSON
  /// @param token 批次去重令牌
  private void insert(String table, List<String> jsonLines, String token) {
    DataStreamWriter body =
        out -> {
          for (String line : jsonLines) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.write('\n');
          }
        };
    Timer.Sample sample = Timer.start(meterRegistry);
    String outcome = "success";
    try {
      client
          .insert(table, body, ClickHouseFormat.JSONEachRow, ClickHouseInserts.settings(token))
          .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
          .close();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      outcome = "unavailable";
      throw new IngestUnavailableException("写 ClickHouse 时被中断", e);
    } catch (Exception e) {
      if (RejectionCodes.rejected(e)) {
        outcome = "rejected";
        throw new IngestRejectedException(rootMessage(e), e);
      }
      outcome = "unavailable";
      throw new IngestUnavailableException("写 ClickHouse 失败：" + rootMessage(e), e);
    } finally {
      sample.stop(
          Timer.builder(INSERT_TIMER)
              .description("一次写进 ClickHouse 的耗时")
              .tag("table", table)
              .tag("outcome", outcome)
              .register(meterRegistry));
    }
  }

  /// 同步执行一条语句，失败当可重试。
  ///
  /// @param sql 语句
  private void execute(String sql) {
    try {
      client.execute(sql).get(TIMEOUT_SECONDS, TimeUnit.SECONDS).close();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IngestUnavailableException("执行 ClickHouse 语句时被中断", e);
    } catch (Exception e) {
      throw new IngestUnavailableException("执行 ClickHouse 语句失败：" + rootMessage(e), e);
    }
  }

  /// 原因链最里层的说明（ClickHouse 的报错原文）。
  ///
  /// @param error 异常
  /// @return 说明
  private static String rootMessage(Throwable error) {
    Throwable root = error;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    return String.valueOf(root.getMessage());
  }
}
