package com.patra.ingest.domain.outbox;

/// 发件箱消息负载的标记接口。
///
/// 实现应该是可以序列化为 JSON 的不可变值对象(record 或 final 类)。
///
/// ### 设计原则
///
/// - **类型安全**: 强制对负载结构进行编译时类型检查
///   - **不可变性**: 负载对象应该是不可变的(使用 record 或 final 字段)
///   - **可序列化性**: 必须可以通过 Jackson 序列化为 JSON
///   - **文档化**: 为下游消费者提供清晰的字段文档
///
/// ### 示例实现
///
/// ```java
/// public record TaskPayload(
///     Long taskId,
///     Long planId,
///     String provenance,
///     String operation,
///     String idempotentKey,
///     Integer priority,
///     Instant scheduledAt
/// ) implements OutboxPayload {
/// ```
///
/// @author linqibin
/// @since 0.1.0
public interface OutboxPayload {
  // 标记接口 - 不需要方法
  // 序列化由框架通过 Jackson ObjectMapper 处理
}
