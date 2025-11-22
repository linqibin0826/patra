package com.patra.common.messaging;

import java.util.Locale;

/// 消息通道的两部分命名约定契约(`领域_资源`)。
/// 
/// **设计理念**：
/// 
/// - **粗粒度路由**：Channel 代表资源级别的路由标识，而非细粒度的事件类型
///   - **关注点分离**：Channel 负责路由，OperationType 负责业务操作语义
///   - **RocketMQ 最佳实践**：少量 Topic（Channel） + Tags 过滤（OperationType）
/// 
/// **目标**:
/// 
/// - 为发布者和消费者提供一致的命名方案
///   - 与特定消息传递实现解耦
///   - 位于 `patra-common` 中，使 API 模块不涉及消息传递细节
/// 
/// **典型用法**:
/// 
/// - 发布者在 Domain 模块中实现此接口（例如 `IngestPublishingChannels`）
///   - 消费者导入已发布的契约以订阅通道
///   - 领域模块可能暴露实现此接口的枚举
/// 
/// **示例**：
/// 
/// ```java
/// public enum IngestPublishingChannels implements ChannelKey {
///   TASK("INGEST", "TASK"),           // channel = "INGEST_TASK"
///   PUBLICATION("INGEST", "PUBLICATION"); // channel = "INGEST_PUBLICATION"
/// 
///   private final String domain;
///   private final String resource;
/// 
///   IngestPublishingChannels(String domain, String resource) {
///     this.domain = domain;
///     this.resource = resource;
/// 
///   @Override
///   public String domain() { return domain;
/// 
///   @Override
///   public String resource() { return resource;
/// ```
/// 
/// @author linqibin
/// @since 0.2.0
public interface ChannelKey {

  /// 业务领域段（例如 `INGEST`、`REGISTRY`、`ANALYSIS`）。
/// 
/// 优先使用与服务边界对齐的大写名称。
/// 
/// @return 领域名称
  String domain();

  /// 资源或聚合段（例如 `TASK`、`PUBLICATION`、`PLAN`）。
/// 
/// 优先使用与核心聚合或业务对象关联的大写名称。
/// 
/// @return 资源名称
  String resource();

  /// 使用下划线构建规范化的大写通道键（例如 `INGEST_TASK`、`INGEST_PUBLICATION`）。
/// 
/// **命名规则**：`<DOMAIN>_<RESOURCE>`
/// 
/// **与 OperationType 的组合**：
/// 
/// - Channel = `INGEST_TASK`（资源级别）
///   - OperationType = `READY`（操作级别）
///   - RocketMQ Destination = `INGEST_TASK:READY`（Topic:Tags 格式）
/// 
/// @return 格式化的通道键（例如 "INGEST_TASK"）
  default String channel() {
    return domain().toUpperCase(Locale.ROOT) + "_" + resource().toUpperCase(Locale.ROOT);
  }
}
