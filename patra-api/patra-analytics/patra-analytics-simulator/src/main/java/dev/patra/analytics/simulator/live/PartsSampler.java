package dev.patra.analytics.simulator.live;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import dev.patra.analytics.simulator.truth.RunScope;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.Clock;
import java.time.temporal.ChronoUnit;

/// 压测期间每 30 秒采一次 ClickHouse 的数据片段与新鲜度 → `parts.csv`（spec §9）。
///
/// 新鲜度 = 现在 − 本运行最新一条事件的 `received_at`（采集端点收到它的时刻），即「收到之后多久能在 ClickHouse 里查到」，
/// D9 的「滞后峰值 ≤ 10 秒、不持续上涨」按它判；还没有事件时为 -1。
public final class PartsSampler {

  /// 表头
  public static final String HEADER =
      "time,events_active_parts,max_parts_in_partition,asr_parts,sq_parts,dpa_parts,merges_running,freshness_seconds";

  private static final String SQL =
      """
      SELECT
        (SELECT count() FROM system.parts WHERE active AND database = currentDatabase() AND table = 'events') AS events_parts,
        (SELECT max(c) FROM (SELECT count() AS c FROM system.parts
            WHERE active AND database = currentDatabase() AND table = 'events' GROUP BY partition)) AS max_parts,
        (SELECT count() FROM system.parts WHERE active AND database = currentDatabase() AND table = 'article_session_reads') AS asr_parts,
        (SELECT count() FROM system.parts WHERE active AND database = currentDatabase() AND table = 'session_quality') AS sq_parts,
        (SELECT count() FROM system.parts WHERE active AND database = currentDatabase() AND table = 'daily_person_activity') AS dpa_parts,
        (SELECT count() FROM system.merges WHERE database = currentDatabase()) AS merges,
        (SELECT if(count() = 0, -1, dateDiff('millisecond', max(received_at), now64(3)))
            FROM events WHERE derived_at > now() - INTERVAL 10 MINUTE AND {run}) AS freshness_ms
      """;

  private final Client client;
  private final RunScope scope;
  private final Clock clock;
  private boolean headerWritten;

  /// 创建采样器。
  ///
  /// @param client ClickHouse 客户端
  /// @param scope 本次运行
  /// @param clock 时钟
  public PartsSampler(Client client, RunScope scope, Clock clock) {
    this.client = client;
    this.scope = scope;
    this.clock = clock;
  }

  /// 采一次，追加一行（第一次先写表头）；查询失败只记日志，不打断压测。
  ///
  /// @param csv 输出
  public synchronized void sampleInto(Writer csv) {
    try {
      GenericRecord row = client.queryAll(SQL.replace("{run}", scope.eventsFilter())).getFirst();
      long freshness = row.getLong("freshness_ms");
      if (!headerWritten) {
        csv.write(HEADER + "\n");
        headerWritten = true;
      }
      csv.write(
          clock.instant().truncatedTo(ChronoUnit.SECONDS)
              + ","
              + row.getLong("events_parts")
              + ","
              + row.getLong("max_parts")
              + ","
              + row.getLong("asr_parts")
              + ","
              + row.getLong("sq_parts")
              + ","
              + row.getLong("dpa_parts")
              + ","
              + row.getLong("merges")
              + ","
              + (freshness < 0 ? "-1" : Double.toString(freshness / 1000.0))
              + "\n");
      csv.flush();
    } catch (IOException e) {
      throw new UncheckedIOException("写 parts.csv 失败", e);
    } catch (RuntimeException e) {
      System.getLogger(PartsSampler.class.getName())
          .log(System.Logger.Level.WARNING, "采样 ClickHouse 失败：" + e.getMessage());
    }
  }
}
