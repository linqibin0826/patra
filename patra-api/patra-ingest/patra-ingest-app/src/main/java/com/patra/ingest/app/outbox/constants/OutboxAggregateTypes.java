package com.patra.ingest.app.outbox.constants;

import lombok.Getter;

/// Outbox 聚合类型枚举。
///
/// 定义 Outbox 框架中使用的所有有效聚合类型,用于:
///
/// - Micrometer 指标标签基数控制
///   - 数据库分区和索引策略
///   - 消息路由和过滤
///
/// ### 使用示例
///
/// ```java
/// @Override
/// protected OutboxAggregateTypes getAggregateType() {
///     return OutboxAggregateTypes.TASK;
/// ```
///
/// ### 配置参考
///
/// 这些值必须与 Nacos 配置中允许的聚合类型匹配:
///
/// ```
///
/// patra:
///   outbox:
///     publisher:
///       allowed-aggregate-types:
///         - Task
///         - TaskRun
///
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see com.patra.ingest.app.outbox.config.OutboxPublisherProperties#getAllowedAggregateTypes()
@Getter
public enum OutboxAggregateTypes {

  /// Task 聚合类型。
  ///
  /// 用于任务队列事件(任务创建、调度、执行)。
  TASK("Task", "Task 聚合 - 用于任务队列事件"),

  /// TaskRun 聚合类型。
  ///
  /// 用于任务执行事件(技术重试、元数据记录)。
  TASK_RUN("TaskRun", "TaskRun 聚合 - 用于执行跟踪和重试"),
  ;

  /// -- GETTER -- 返回聚合类型代码。
  ///
  /// 此值存储在字段中。
  private final String code;

  /// -- GETTER -- 返回人类可读的描述。
  private final String description;

  OutboxAggregateTypes(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 根据代码查找枚举。
  ///
  /// @param code 聚合类型代码
  /// @return 匹配的枚举值
  /// @throws IllegalArgumentException 如果未找到代码
  public static OutboxAggregateTypes fromCode(String code) {
    for (OutboxAggregateTypes type : values()) {
      if (type.code.equals(code)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的聚合类型代码: " + code);
  }
}
