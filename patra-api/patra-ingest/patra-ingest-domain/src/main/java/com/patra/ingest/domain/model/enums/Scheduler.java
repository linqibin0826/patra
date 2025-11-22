package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/// 调度器来源枚举 (字典: ing_scheduler)。
///
/// **持久化约定**
///
/// - 列名: **scheduler_code**
///   - 位置: `ing_schedule_instance.scheduler_code`
///   - 定义: `VARCHAR(32) NOT NULL DEFAULT 'XXL'` 带注释 "DICT CODE(type=ing_scheduler)"
///   - 存储值: {@link #getCode()} (例如,`"XXL"`)
///
/// **值与语义**
///
/// - XXL — XXL-Job 调度器(外部分布式调度中心)
///   - SPRING — Spring 应用内调度器(@Scheduled)
///   - QUARTZ — Quartz 调度器(应用级或集群级)
///
/// **转换契约**
///
/// - 通过 {@link #getCode()} 输出代码。
///   - 使用 {@link #fromCode(String)} 解析;去空格并转大写;未知值抛出 {@link IllegalArgumentException}。
///
/// **演进保护措施**
///
/// - 添加新值时保持 **ing_scheduler** 字典、默认值、验证和文档同步。
///   - 更改默认值需要 DDL/迁移和种子数据更新。
///
/// **层级位置:** 领域枚举,无框架依赖。
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum Scheduler {

  /// XXL-Job 调度器;外部分布式调度中心。
  XXL("XXL", "XXL-Job scheduler"),
  /// Spring 调度器;应用内定时任务。
  SPRING("SPRING", "Spring scheduled tasks"),
  /// Quartz 调度器;应用级或集群级调度器。
  QUARTZ("QUARTZ", "Quartz scheduler");

  /// 持久化到 `scheduler_code` 的字典代码。
  private final String code;

  /// 用于展示或文档的可读描述。
  private final String description;

  Scheduler(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 解析字典代码,忽略大小写和前后空格。
  ///
  /// @param value 字符串代码,如 `"XXL"`、`"spring"` 或 `" Quartz "`
  /// @return 匹配的 {@link Scheduler}
  /// @throws IllegalArgumentException 当值为 null 或无法识别时
  public static Scheduler fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("调度器代码不能为 null");
    }
    String normalized = value.trim().toUpperCase();
    for (Scheduler type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的调度器代码: " + value);
  }
}
