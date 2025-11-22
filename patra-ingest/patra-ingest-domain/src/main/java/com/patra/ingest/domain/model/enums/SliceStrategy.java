package com.patra.ingest.domain.model.enums;

import java.util.Optional;

/// 切片策略枚举,定义所有支持的窗口类型。
///
/// 此枚举用于计划级窗口规范和切片级策略。
///
/// 策略类型:
///
/// - TIME: 基于时间的窗口化,带时间戳精度(例如,2024-01-01T00:00:00Z 到 2024-12-31T23:59:59Z)
///   - DATE: 仅日期的窗口化,无时间部分(例如,2024-01-01 到 2024-12-31)
///   - ID_RANGE: ID 范围窗口化(例如,ID 1000000 到 2000000)
///   - CURSOR_LANDMARK: 基于游标/令牌的窗口化(例如,分页令牌)
///   - VOLUME_BUDGET: 基于数量的窗口化(例如,最多获取 100k 条记录)
///   - HYBRID: 组合策略(例如,时间 + ID + 数量约束)
///   - SINGLE: 单切片(无分区,通常用于 UPDATE 操作)
///
/// @author linqibin
/// @since 0.1.0
public enum SliceStrategy {

  /// 时间型;基于时间的窗口化策略(包含带时间部分的时间戳)。
  TIME("TIME"),

  /// 日期型;仅日期的窗口化策略(天级粒度,无时间部分)。
  DATE("DATE"),

  /// ID 范围型;基于 ID 范围的窗口化策略。
  ID_RANGE("ID_RANGE"),

  /// 游标地标型;基于游标/令牌的窗口化策略。
  CURSOR_LANDMARK("CURSOR_LANDMARK"),

  /// 数量预算型;基于数量预算的窗口化策略。
  VOLUME_BUDGET("VOLUME_BUDGET"),

  /// 混合型;组合多种约束的策略。
  HYBRID("HYBRID"),

  /// 单切片型;单切片策略(无分区)。
  SINGLE("SINGLE");

  private final String code;

  SliceStrategy(String code) {
    this.code = code;
  }

  /// 返回用于持久化和 JSON 序列化的策略代码。
  public String getCode() {
    return code;
  }

  /// 从代码字符串解析策略枚举。
  ///
  /// @param code 策略代码
  /// @return 包装在 Optional 中的匹配策略枚举
  public static Optional<SliceStrategy> fromCode(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    for (SliceStrategy strategy : values()) {
      if (strategy.code.equalsIgnoreCase(code)) {
        return Optional.of(strategy);
      }
    }
    return Optional.empty();
  }
}
