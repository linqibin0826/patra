package com.patra.ingest.domain.outbox;

/**
 * 发件箱消息头的标记接口。
 *
 * <p>消息头包含用于追踪、路由和调试目的的元数据。实现应该是不可变值对象(record 或 final 类)。
 *
 * <h3>设计原则</h3>
 *
 * <ul>
 *   <li><b>类型安全</b>: 强制对消息头结构进行编译时类型检查
 *   <li><b>不可变性</b>: 消息头对象应该是不可变的(使用 record 或 final 字段)
 *   <li><b>可序列化性</b>: 必须可以通过 Jackson 序列化为 JSON
 *   <li><b>可观测性</b>: 应该包含追踪和关联标识符
 * </ul>
 *
 * <h3>常见消息头字段</h3>
 *
 * <ul>
 *   <li><b>追踪</b>: scheduleInstanceId, traceId, correlationId
 *   <li><b>时间</b>: triggeredAt, occurredAt, publishedAt
 *   <li><b>来源</b>: scheduler, schedulerJobId, sourceSystem
 *   <li><b>元数据</b>: version, eventType, causationId
 * </ul>
 *
 * <h3>示例实现</h3>
 *
 * <pre>{@code
 * public record TaskHeaders(
 *     Long scheduleInstanceId,
 *     String scheduler,
 *     String schedulerJobId,
 *     Instant triggeredAt,
 *     Instant occurredAt
 * ) implements OutboxHeaders {
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface OutboxHeaders {
  // 标记接口 - 不需要方法
  // 序列化由框架通过 Jackson ObjectMapper 处理
}
