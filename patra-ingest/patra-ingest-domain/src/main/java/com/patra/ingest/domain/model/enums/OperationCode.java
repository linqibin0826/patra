package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/// 采集操作类型 (字典: ing_operation)。
///
/// **持久化映射**
///
/// - ing_plan.operation_code → HARVEST/BACKFILL/UPDATE/METRICS
///   - ing_task.operation_code → HARVEST/BACKFILL/UPDATE/METRICS
///   - ing_cursor.operation_code → HARVEST/BACKFILL/UPDATE/METRICS
///   - ing_cursor_event.operation_code → HARVEST/BACKFILL/UPDATE/METRICS
///
/// **解析/输出契约**
///
/// - 始终通过 {@link #getCode()} 输出大写值。
///   - 使用 {@link #fromCode(String)} 解析,该方法会去空格并转大写;未知值抛出 {@link IllegalArgumentException}。
///
/// **扩展策略:** 添加新操作类型时更新上游配置和字典表以保持向后兼容性。
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum OperationCode {
  /// 全量采集;初次运行或重建窗口的全量数据采集。
  HARVEST("HARVEST", "Full ingestion"),
  /// 历史回填;填补数据缺口或修正历史数据。
  BACKFILL("BACKFILL", "Backfill ingestion"),
  /// 增量更新;基于游标推进的增量数据更新。
  UPDATE("UPDATE", "Incremental update"),
  /// 指标采集;面向指标统计的操作(读取密集型)。
  METRICS("METRICS", "Metrics collection");

  private final String code;
  private final String description;

  OperationCode(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 将提供的代码解析为枚举值。
  ///
  /// @param value 字符串代码(例如,`"harvest"` 或 `" UPDATE "`)
  /// @return 匹配的枚举值
  /// @throws IllegalArgumentException 当值为 null 或未知时
  public static OperationCode fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("操作代码不能为 null");
    }
    String normalized = value.trim().toUpperCase();
    for (OperationCode oc : values()) {
      if (oc.code.equals(normalized)) {
        return oc;
      }
    }
    throw new IllegalArgumentException("未知的操作代码: " + value);
  }
}
