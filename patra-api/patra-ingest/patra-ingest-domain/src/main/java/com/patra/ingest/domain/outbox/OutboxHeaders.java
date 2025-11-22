package com.patra.ingest.domain.outbox;

/// 发件箱消息头的标记接口。
///
/// 消息头包含用于追踪、路由和调试目的的元数据。实现应该是不可变值对象(record 或 final 类)。
///
/// ### 设计原则
///
/// - **类型安全**: 强制对消息头结构进行编译时类型检查
///   - **不可变性**: 消息头对象应该是不可变的(使用 record 或 final 字段)
///   - **可序列化性**: 必须可以通过 Jackson 序列化为 JSON
///   - **可观测性**: 应该包含追踪和关联标识符
///
/// ### 常见消息头字段
///
/// - **追踪**: scheduleInstanceId, traceId, correlationId
///   - **时间**: triggeredAt, occurredAt, publishedAt
///   - **来源**: scheduler, schedulerJobId, sourceSystem
///   - **元数据**: version, eventType, causationId
///
/// ### 示例实现
///
/// ```java
/// public record TaskHeaders(
///     Long scheduleInstanceId,
///     String scheduler,
///     String schedulerJobId,
///     Instant triggeredAt,
///     Instant occurredAt
/// ) implements OutboxHeaders {
/// ```
///
/// @author linqibin
/// @since 0.1.0
public interface OutboxHeaders {
  // 标记接口 - 不需要方法
  // 序列化由框架通过 Jackson ObjectMapper 处理
}
