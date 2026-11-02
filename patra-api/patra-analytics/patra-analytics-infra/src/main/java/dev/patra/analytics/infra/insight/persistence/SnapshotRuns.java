package dev.patra.analytics.infra.insight.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;

/// `insight.snapshot_run` 的写法：两种快照的写入适配器共用。
final class SnapshotRuns {

  /// 热读快照。
  static final String HOT_READING = "HOT_READING";

  /// 共现快照。
  static final String CO_READING = "CO_READING";

  /// 批量插入时每条语句的行数。
  static final int CHUNK = 5_000;

  private SnapshotRuns() {}

  /// 记下这次成功计算的时间与行数。
  ///
  /// @param jdbc 客户端
  /// @param kind 快照种类
  /// @param computedAt 计算时间
  /// @param rowCount 行数
  static void record(JdbcClient jdbc, String kind, Instant computedAt, int rowCount) {
    jdbc.sql(
            "INSERT INTO insight.snapshot_run (kind, computed_at, row_count)"
                + " VALUES (:kind, :at, :rows)"
                + " ON CONFLICT (kind) DO UPDATE"
                + " SET computed_at = EXCLUDED.computed_at, row_count = EXCLUDED.row_count")
        .param("kind", kind)
        .param("at", computedAt.atOffset(ZoneOffset.UTC))
        .param("rows", rowCount)
        .update();
  }
}
