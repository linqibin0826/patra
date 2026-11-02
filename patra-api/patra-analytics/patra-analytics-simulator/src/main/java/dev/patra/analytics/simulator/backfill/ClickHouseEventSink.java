package dev.patra.analytics.simulator.backfill;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.ServerException;
import com.clickhouse.client.api.insert.InsertSettings;
import com.clickhouse.client.api.query.GenericRecord;
import com.clickhouse.data.ClickHouseFormat;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/// 用 ClickHouse 官方客户端写 `events`，写入设置与入库完全一致（spec §8，`ClickHouseInserts`）。
public final class ClickHouseEventSink implements EventSink {

  /// `NOT_ENOUGH_SPACE`
  static final int NOT_ENOUGH_SPACE = 243;

  private static final System.Logger LOG = System.getLogger(ClickHouseEventSink.class.getName());
  private static final long INSERT_TIMEOUT_MINUTES = 10;

  private final Client client;

  /// 创建写入端。
  ///
  /// @param client 客户端（默认库指向 `patra_analytics`，开请求压缩）
  public ClickHouseEventSink(Client client) {
    this.client = client;
  }

  /// 一次写入的设置：与入库的五项相同（令牌、物化视图按令牌去重、null 写默认值、时间宽松解析、对象写成字符串）。
  ///
  /// @param token 批次去重令牌
  /// @return 设置
  static InsertSettings settings(String token) {
    return new InsertSettings()
        .setDeduplicationToken(token)
        .serverSetting("deduplicate_blocks_in_dependent_materialized_views", "1")
        .serverSetting("input_format_null_as_default", "1")
        .serverSetting("date_time_input_format", "best_effort")
        .serverSetting("input_format_json_read_objects_as_strings", "1");
  }

  /// 写一批，流式写出请求体（不先拼成整批的字符串）。
  ///
  /// @param lines 行的提供者
  /// @param token 批次去重令牌
  @Override
  public void insert(Supplier<Iterator<String>> lines, String token) {
    try {
      client
          .insert(
              "events",
              out -> {
                Iterator<String> it = lines.get();
                while (it.hasNext()) {
                  out.write(it.next().getBytes(StandardCharsets.UTF_8));
                  out.write('\n');
                }
              },
              ClickHouseFormat.JSONEachRow,
              settings(token))
          .get(INSERT_TIMEOUT_MINUTES, TimeUnit.MINUTES)
          .close();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("写 ClickHouse 时被中断", e);
    } catch (Exception e) {
      for (Throwable cause = e; cause != null; cause = cause.getCause()) {
        if (cause instanceof ServerException server && server.getCode() == NOT_ENOUGH_SPACE) {
          throw new DiskFullException("ClickHouse 磁盘到了红线：" + server.getMessage(), e);
        }
      }
      throw new IllegalStateException("写 ClickHouse 失败：" + e.getMessage(), e);
    }
  }

  /// 一天写完后打印 `events` 的活跃片段数，观察合并是否跟得上。
  ///
  /// @param day 日期
  @Override
  public void afterDay(LocalDate day) {
    List<GenericRecord> rows =
        client.queryAll(
            "SELECT count() AS parts FROM system.parts"
                + " WHERE active AND database = currentDatabase() AND table = 'events'");
    LOG.log(
        System.Logger.Level.INFO,
        "{0} 写完，events 活跃片段 {1} 个",
        day,
        rows.getFirst().getLong("parts"));
  }
}
