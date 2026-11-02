package dev.patra.analytics.simulator.live;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import dev.patra.analytics.simulator.truth.RunScope;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/// 压测期间每 30 秒采一次 ClickHouse 的数据片段与新鲜度 → `parts.csv`（spec §9）。
///
/// 新鲜度 = 现在 − 本运行最新一条事件的 `received_at`（采集端点收到它的时刻），即「收到之后多久能在 ClickHouse 里查到」，
/// D9 的「滞后峰值 ≤ 10 秒、不持续上涨」按它判；还没有事件时为 -1。
///
/// 合并数 = 最近一个采样间隔里完成的合并次数，取自 `system.part_log`（`patra` 账号读不了 `system.merges`）。
/// 它单独查：查不到时这一列留空，片段数与新鲜度照常记。
public final class PartsSampler {

  private static final System.Logger LOG = System.getLogger(PartsSampler.class.getName());

  /// 表头
  public static final String HEADER =
      "time,events_active_parts,max_parts_in_partition,asr_parts,sq_parts,dpa_parts,merges_finished,freshness_seconds";

  private static final String SQL =
      """
      SELECT
        (SELECT count() FROM system.parts WHERE active AND database = currentDatabase() AND table = 'events') AS events_parts,
        (SELECT max(c) FROM (SELECT count() AS c FROM system.parts
            WHERE active AND database = currentDatabase() AND table = 'events' GROUP BY partition)) AS max_parts,
        (SELECT count() FROM system.parts WHERE active AND database = currentDatabase() AND table = 'article_session_reads') AS asr_parts,
        (SELECT count() FROM system.parts WHERE active AND database = currentDatabase() AND table = 'session_quality') AS sq_parts,
        (SELECT count() FROM system.parts WHERE active AND database = currentDatabase() AND table = 'daily_person_activity') AS dpa_parts,
        (SELECT if(count() = 0, -1, dateDiff('millisecond', max(received_at), now64(3)))
            FROM events WHERE derived_at > now() - INTERVAL 10 MINUTE AND {run}) AS freshness_ms
      """;

  private static final String MERGES_SQL =
      """
      SELECT count() AS n FROM system.part_log
      WHERE event_date >= toDate(now() - INTERVAL {window} SECOND)
        AND event_time > now() - INTERVAL {window} SECOND
        AND event_type = 'MergeParts' AND database = currentDatabase()
      """;

  private final Client client;
  private final RunScope scope;
  private final Clock clock;
  private final String mergesSql;
  private boolean headerWritten;
  private boolean mergesFailureLogged;

  /// 创建采样器。
  ///
  /// @param client ClickHouse 客户端
  /// @param scope 本次运行
  /// @param clock 时钟
  /// @param window 合并数统计的时间窗（与采样间隔相同）
  public PartsSampler(Client client, RunScope scope, Clock clock, Duration window) {
    this.client = client;
    this.scope = scope;
    this.clock = clock;
    this.mergesSql = MERGES_SQL.replace("{window}", Long.toString(window.toSeconds()));
  }

  /// 采一次，追加一行（第一次先写表头）；片段与新鲜度查询失败只记日志、不打断压测，合并数查不到只空这一列。
  ///
  /// @param csv 输出
  public synchronized void sampleInto(Writer csv) {
    try {
      GenericRecord row = client.queryAll(SQL.replace("{run}", scope.eventsFilter())).getFirst();
      long freshness = row.getLong("freshness_ms");
      String merges = mergesFinished();
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
              + merges
              + ","
              + (freshness < 0 ? "-1" : Double.toString(freshness / 1000.0))
              + "\n");
      csv.flush();
    } catch (IOException e) {
      throw new UncheckedIOException("写 parts.csv 失败", e);
    } catch (RuntimeException e) {
      LOG.log(System.Logger.Level.WARNING, "采样 ClickHouse 失败：" + e.getMessage());
    }
  }

  /// 最近一个间隔里完成的合并次数；查不到时为空串（只在第一次失败时告警，不每 30 秒刷一遍）。
  ///
  /// @return 次数或空串
  private String mergesFinished() {
    try {
      return Long.toString(client.queryAll(mergesSql).getFirst().getLong("n"));
    } catch (RuntimeException e) {
      if (!mergesFailureLogged) {
        LOG.log(System.Logger.Level.WARNING, "查合并次数失败，parts.csv 的合并列留空：" + e.getMessage());
        mergesFailureLogged = true;
      }
      return "";
    }
  }
}
