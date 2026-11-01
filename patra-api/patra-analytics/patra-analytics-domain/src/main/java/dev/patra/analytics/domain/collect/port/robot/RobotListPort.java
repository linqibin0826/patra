package dev.patra.analytics.domain.collect.port.robot;

/// COUNTER-Robots 爬虫 UA 列表。
public interface RobotListPort {

  /// UA 是否匹配列表里的任一模式（不区分大小写）。
  ///
  /// @param userAgent 原始 UA
  /// @return 匹配时为 `true`
  boolean listed(String userAgent);
}
