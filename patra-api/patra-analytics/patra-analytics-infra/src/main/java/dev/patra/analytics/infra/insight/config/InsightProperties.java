package dev.patra.analytics.infra.insight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// insight 的配置（`patra.analytics.*`）。
///
/// @param includeSynthetic 对外数字是否计入合成事件；默认不计，mini 上一直关着，只在本机开发时打开（insight spec §3.1）
/// @param insight insight 自己的设置
@ConfigurationProperties("patra.analytics")
public record InsightProperties(
    @DefaultValue("false") boolean includeSynthetic, @DefaultValue Insight insight) {

  /// 构造。
  ///
  /// @param includeSynthetic 是否计入合成事件
  /// @param coReadingShards 共现查询的分片数
  /// @return 配置
  public static InsightProperties of(boolean includeSynthetic, int coReadingShards) {
    return new InsightProperties(includeSynthetic, new Insight(coReadingShards));
  }

  /// ClickHouse 查询里过滤合成事件的条件（前面带 `AND`）；计入合成事件时为空串。
  ///
  /// @param column 列名
  /// @return SQL 片段
  public String syntheticFilter(String column) {
    return includeSynthetic ? "" : " AND " + column + " = 0";
  }

  /// insight 自己的设置（`patra.analytics.insight.*`）。
  ///
  /// @param coReadingShards 共现查询按文献分几片跑，每片一条查询（守住每条查询 1 GiB 内存）
  public record Insight(@DefaultValue("8") int coReadingShards) {}
}
