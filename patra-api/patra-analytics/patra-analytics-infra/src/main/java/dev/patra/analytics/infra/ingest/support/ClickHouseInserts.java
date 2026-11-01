package dev.patra.analytics.infra.ingest.support;

import com.clickhouse.client.api.insert.InsertSettings;

/// 入库写入 ClickHouse 的设置（入库设计 §4.2）。
public final class ClickHouseInserts {

  private ClickHouseInserts() {}

  /// 一次 `INSERT ... FORMAT JSONEachRow` 的设置。
  ///
  /// - 带批次去重令牌；物化视图的目标表也按令牌去重（否则物化视图失败后重试会永久缺这一批）
  /// - JSON 的 `null` 写成列默认值；时间直接认 RFC 3339；`properties` 对象原样写进 String 列
  ///
  /// @param token 批次去重令牌
  /// @return 写入设置
  public static InsertSettings settings(String token) {
    return new InsertSettings()
        .setDeduplicationToken(token)
        .serverSetting("deduplicate_blocks_in_dependent_materialized_views", "1")
        .serverSetting("input_format_null_as_default", "1")
        .serverSetting("date_time_input_format", "best_effort")
        .serverSetting("input_format_json_read_objects_as_strings", "1");
  }
}
