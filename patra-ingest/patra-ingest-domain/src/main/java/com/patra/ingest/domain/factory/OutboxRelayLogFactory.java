package com.patra.ingest.domain.factory;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.entity.OutboxRelayLog;
import com.patra.ingest.domain.model.enums.RelayStatus;
import com.patra.ingest.domain.model.vo.relay.RelayBatchId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/// OutboxRelayLog 领域实体工厂。
/// 
/// 职责:
/// 
/// - 在领域层封装 Relay 日志构造逻辑
///   - 使用 OutboxMessage 的领域方法(如 computeNextAttempt)
///   - 确保不同 Relay 结果的日志创建一致性
/// 
/// 设计原则:
/// 
/// - **纯 Java**: 无 Spring 依赖(不使用 @Component 注解)
///   - **实例工厂**: 可注入 Clock 以支持确定性测试
///   - **单一职责**: 仅构造 OutboxRelayLog 实例
///   - **领域规则**: 委托给 OutboxMessage 执行业务逻辑
/// 
/// 用法示例:
/// 
/// ```java
/// // Boot 层注册为 Bean:
/// @Bean
/// public OutboxRelayLogFactory factory(Clock clock) {
///   return new OutboxRelayLogFactory(clock);
/// 
/// // Application 层使用注入的工厂:
/// OutboxRelayLog log = factory.createForPublished(message, batchId, ...);
/// ```
/// 
/// @author Patra Team
/// @since 0.1.0
public class OutboxRelayLogFactory {

  private final Clock clock;

  /// 构造工厂实例。
/// 
/// @param clock 时钟实例,用于生成时间戳(可注入以支持测试)
  public OutboxRelayLogFactory(Clock clock) {
    this.clock = clock;
  }

  /// 为租约获取失败创建 Relay 日志(并发竞争场景)。
/// 
/// 场景:另一个实例先获得了租约。
/// 
/// @param message 未能获取租约的 Outbox 消息
/// @param batchId Relay 批次标识符
/// @param leaseOwner 尝试获取租约的实例标识符
/// @param startTime Relay 尝试开始时间戳
/// @return 状态为 LEASE_MISSED 的 OutboxRelayLog
  public OutboxRelayLog createForLeaseMissed(
      OutboxMessage message, RelayBatchId batchId, String leaseOwner, Instant startTime) {

    Instant completedAt = Instant.now(clock);

    return OutboxRelayLog.builder()
        .outboxMessageId(message.getId())
        .relayBatchId(batchId.getValue())
        .channel(message.getChannel())
        .partitionKey(message.getPartitionKey())
        .leaseOwner(leaseOwner)
        .attemptNumber(message.computeNextAttempt()) // ✅ Uses domain method
        .relayStatus(RelayStatus.LEASE_MISSED)
        .startedAt(startTime)
        .completedAt(completedAt)
        .durationMs((int) Duration.between(startTime, completedAt).toMillis())
        .build();
  }

  /// 为消息发布成功创建 Relay 日志。
/// 
/// 场景:消息成功发送到下游消息中间件。
/// 
/// @param message 已发布的 Outbox 消息
/// @param batchId Relay 批次标识符
/// @param leaseOwner 发布消息的实例标识符
/// @param startTime Relay 尝试开始时间戳
/// @param publishedAt 消息确认发布的时间戳
/// @return 状态为 PUBLISHED 的 OutboxRelayLog
  public OutboxRelayLog createForPublished(
      OutboxMessage message,
      RelayBatchId batchId,
      String leaseOwner,
      Instant startTime,
      Instant publishedAt) {

    return OutboxRelayLog.builder()
        .outboxMessageId(message.getId())
        .relayBatchId(batchId.getValue())
        .channel(message.getChannel())
        .partitionKey(message.getPartitionKey())
        .leaseOwner(leaseOwner)
        .attemptNumber(message.computeNextAttempt()) // ✅ Uses domain method
        .relayStatus(RelayStatus.PUBLISHED)
        .startedAt(startTime)
        .completedAt(publishedAt)
        .durationMs((int) Duration.between(startTime, publishedAt).toMillis())
        .build();
  }

  /// 为延迟重试创建 Relay 日志(临时性错误)。
/// 
/// 场景:发布失败但错误可重试,将在退避后重试。
/// 
/// @param message 遇到临时性错误的 Outbox 消息
/// @param batchId Relay 批次标识符
/// @param leaseOwner 尝试发布的实例标识符
/// @param startTime Relay 尝试开始时间戳
/// @param nextRetryAt 下次重试的计划时间戳
/// @param errorCode 错误分类代码(如 NETWORK_TIMEOUT)
/// @param errorMessage 详细错误消息(截断至 512 字符)
/// @param errorKind 错误类型: FATAL 或 TRANSIENT
/// @return 状态为 DEFERRED 的 OutboxRelayLog
  public OutboxRelayLog createForDeferred(
      OutboxMessage message,
      RelayBatchId batchId,
      String leaseOwner,
      Instant startTime,
      Instant nextRetryAt,
      String errorCode,
      String errorMessage,
      String errorKind) {

    Instant completedAt = Instant.now(clock);

    return OutboxRelayLog.builder()
        .outboxMessageId(message.getId())
        .relayBatchId(batchId.getValue())
        .channel(message.getChannel())
        .partitionKey(message.getPartitionKey())
        .leaseOwner(leaseOwner)
        .attemptNumber(message.computeNextAttempt()) // ✅ Uses domain method
        .relayStatus(RelayStatus.DEFERRED)
        .errorCode(errorCode)
        .errorMessage(truncate(errorMessage, 512))
        .errorKind(errorKind)
        .nextRetryAt(nextRetryAt)
        .startedAt(startTime)
        .completedAt(completedAt)
        .durationMs((int) Duration.between(startTime, completedAt).toMillis())
        .build();
  }

  /// 为永久失败创建 Relay 日志(达到最大重试次数或致命错误)。
/// 
/// 场景:发布永久失败,不再重试。
/// 
/// @param message 永久失败的 Outbox 消息
/// @param batchId Relay 批次标识符
/// @param leaseOwner 尝试发布的实例标识符
/// @param startTime Relay 尝试开始时间戳
/// @param errorCode 错误分类代码
/// @param errorMessage 详细错误消息(截断至 512 字符)
/// @param errorKind 错误类型: FATAL 或 TRANSIENT
/// @return 状态为 FAILED 的 OutboxRelayLog
  public OutboxRelayLog createForFailed(
      OutboxMessage message,
      RelayBatchId batchId,
      String leaseOwner,
      Instant startTime,
      String errorCode,
      String errorMessage,
      String errorKind) {

    Instant completedAt = Instant.now(clock);

    return OutboxRelayLog.builder()
        .outboxMessageId(message.getId())
        .relayBatchId(batchId.getValue())
        .channel(message.getChannel())
        .partitionKey(message.getPartitionKey())
        .leaseOwner(leaseOwner)
        .attemptNumber(message.computeNextAttempt()) // ✅ Uses domain method
        .relayStatus(RelayStatus.FAILED)
        .errorCode(errorCode)
        .errorMessage(truncate(errorMessage, 512))
        .errorKind(errorKind)
        .startedAt(startTime)
        .completedAt(completedAt)
        .durationMs((int) Duration.between(startTime, completedAt).toMillis())
        .build();
  }

  /// 将错误消息截断至最大长度。
/// 
/// @param str 原始字符串
/// @param maxLength 允许的最大长度
/// @return 截断后的字符串(如果输入为 null 则返回 null)
  private String truncate(String str, int maxLength) {
    if (str == null) {
      return null;
    }
    return str.length() > maxLength ? str.substring(0, maxLength) : str;
  }
}
