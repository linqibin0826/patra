package com.patra.ingest.app.usecase.relay.coordinator;

import com.patra.ingest.app.usecase.relay.metrics.OutboxRelayMetrics;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.entity.OutboxRelayLog;
import com.patra.ingest.domain.model.enums.RelayStatus;
import com.patra.ingest.domain.model.vo.relay.RelayBatchId;
import com.patra.ingest.domain.port.OutboxRelayLogRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 中继日志协调器 - 管理中继执行审计跟踪
///
/// ### 职责
///
/// - 创建中继执行日志
///   - 在批次执行期间在内存中累积日志
///   - 批量持久化日志到数据库以提高性能
///   - 支持所有中继结果: PUBLISHED, DEFERRED, FAILED, LEASE_MISSED
///
/// ### 批处理模式
///
/// ```
///
/// LogAccumulator acc = coordinator.createAccumulator(batchId);
/// for (OutboxMessage msg : batch) {
///   if (!leaseAcquired) {
///     acc.recordLeaseMissed(...);
///   } else if (publishSuccess) {
///     acc.recordPublished(...);
///   } else if (retryable) {
///     acc.recordDeferred(...);
///   } else {
///     acc.recordFailed(...);
///   }
/// }
/// coordinator.persistBatch(acc); // 单条 INSERT 语句处理 N 行
///
/// ```
///
/// ### 性能优化
///
/// - 批量插入: 100-500 条日志在单个 SQL 语句中持久化
///   - 将数据库往返次数从 N 减少到 1
///   - 提高高频中继作业的吞吐量
///
/// ### 日志策略
///
/// - DEBUG: 批量持久化操作 (大小、受影响的行数、批次 ID)
///   - 无单条消息日志 (由其他协调器处理)
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayLogCoordinator {

  private final Clock clock;
  private final OutboxRelayLogRepository logRepository;
  private final OutboxRelayMetrics metrics;

  /// 为中继批次创建新的日志累加器
  ///
  /// 累加器在批次执行期间在内存中收集日志,并使用 {@link #persistBatch(LogAccumulator)} 一次性持久化所有日志
  ///
  /// @param batchId 用于分组日志的中继批次标识符
  /// @return 准备记录中继结果的空日志累加器
  public LogAccumulator createAccumulator(RelayBatchId batchId) {
    return new LogAccumulator(batchId, clock);
  }

  /// 批量持久化累积的中继日志到数据库并记录指标
  ///
  /// 使用单个 SQL INSERT 语句处理多行以提高性能
  ///
  /// 生成的 SQL 示例:
  ///
  /// ```
  ///
  /// INSERT INTO ing_outbox_relay_log (message_id, relay_batch_id, ...)
  /// VALUES (1, 'batch-001', ...), (2, 'batch-001', ...), ...;
  ///
  /// ```
  ///
  /// 持久化后,为每个中继结果记录指标:
  ///
  /// - PUBLISHED: `outbox.relay.attempts{channel=X, status=PUBLISHED`}
  ///   - DEFERRED: `outbox.relay.attempts{channel=X, status=DEFERRED`} + 错误计数器
  ///   - FAILED: `outbox.relay.attempts{channel=X, status=FAILED`} + 错误计数器
  ///   - LEASE_MISSED: `outbox.relay.attempts{channel=X, status=LEASE_MISSED`}
  ///
  /// @param accumulator 包含收集的中继日志的日志累加器
  public void persistBatch(LogAccumulator accumulator) {
    if (accumulator.isEmpty()) {
      log.debug("批次 ID={} 没有中继日志需要持久化", accumulator.batchId.getValue());
      return;
    }

    List<OutboxRelayLog> logs = accumulator.getLogs();
    logRepository.saveBatch(logs);

    // 为每个中继结果记录指标
    for (OutboxRelayLog relayLog : logs) {
      recordMetrics(relayLog);
    }
  }

  /// 为单个中继日志条目记录指标
  ///
  /// 根据中继状态分派到适当的指标记录方法
  ///
  /// @param relayLog 要从中提取指标的中继日志
  private void recordMetrics(OutboxRelayLog relayLog) {
    String channel = relayLog.getChannel();
    RelayStatus status = relayLog.getRelayStatus();

    // 移除 default 分支，让编译器强制检查枚举完整性
    // 如果新增 RelayStatus 值，编译器会强制要求在此处理
    switch (status) {
      case PUBLISHED -> metrics.recordPublished(channel);
      case DEFERRED -> metrics.recordDeferred(channel, relayLog.getErrorCode());
      case FAILED -> metrics.recordFailed(channel, relayLog.getErrorCode());
      case LEASE_MISSED -> metrics.recordLeaseMissed(channel);
    }
  }

  /// 用于在批处理期间收集中继执行日志的日志累加器
  ///
  /// 线程安全性: 非线程安全,应仅在单个线程中使用 (典型的中继作业执行模式)
  public class LogAccumulator {

    private final RelayBatchId batchId;
    private final Clock clock;
    private final List<OutboxRelayLog> logs;

    private LogAccumulator(RelayBatchId batchId, Clock clock) {
      this.batchId = batchId;
      this.clock = clock;
      this.logs = new ArrayList<>();
    }

    /// 创建 OutboxRelayLog 的基础 Builder,包含所有日志共有的字段
    ///
    /// @param message Outbox 消息
    /// @param leaseOwner 租约持有者标识符
    /// @param startTime 中继开始时间戳
    /// @return 已填充共有字段的 Builder
    /// @throws NullPointerException 如果任何参数为 null
    /// @throws IllegalArgumentException 如果 leaseOwner 为空字符串
    private OutboxRelayLog.OutboxRelayLogBuilder createBaseBuilder(
        OutboxMessage message, String leaseOwner, Instant startTime) {

      // 参数验证
      if (message == null) {
        throw new NullPointerException("message 不能为 null");
      }
      if (leaseOwner == null) {
        throw new NullPointerException("leaseOwner 不能为 null");
      }
      if (leaseOwner.isBlank()) {
        throw new IllegalArgumentException("leaseOwner 不能为空字符串");
      }
      if (startTime == null) {
        throw new NullPointerException("startTime 不能为 null");
      }

      return OutboxRelayLog.builder()
          .outboxMessageId(message.getId())
          .relayBatchId(batchId.getValue())
          .channel(message.getChannel())
          .partitionKey(message.getPartitionKey())
          .leaseOwner(leaseOwner)
          .attemptNumber(message.computeNextAttempt())
          .startedAt(startTime);
    }

    /// 计算中继执行持续时间(毫秒)
    ///
    /// 如果 endTime 早于 startTime (系统时间回拨), 返回 0 并记录警告。
    /// 如果持续时间超过 int 范围 (超过 24.8 天), 截断为 Integer.MAX_VALUE 并记录警告。
    ///
    /// @param startTime 开始时间
    /// @param endTime 结束时间
    /// @return 持续时间(毫秒), 最小值为 0, 最大值为 Integer.MAX_VALUE
    private int computeDuration(Instant startTime, Instant endTime) {
      long durationMs = Duration.between(startTime, endTime).toMillis();

      if (durationMs < 0) {
        log.warn(
            "检测到时间倒流: startTime={}, endTime={}, 将持续时间设为 0",
            startTime,
            endTime);
        return 0;
      }

      if (durationMs > Integer.MAX_VALUE) {
        log.warn("持续时间过长: {}ms, 截断为 Integer.MAX_VALUE", durationMs);
        return Integer.MAX_VALUE;
      }

      return (int) durationMs;
    }

    /// 截断错误消息到最大长度
    ///
    /// - null → null
    /// - 空字符串或纯空白 → null (避免存储无意义数据)
    /// - 超长字符串 → 截断并添加省略标记 "..."
    ///
    /// @param errorMessage 原始错误消息
    /// @param maxLength 最大长度
    /// @return 截断后的错误消息
    private String truncateErrorMessage(String errorMessage, int maxLength) {
      if (errorMessage == null || errorMessage.isBlank()) {
        return null;
      }

      String trimmed = errorMessage.trim();
      if (trimmed.length() <= maxLength) {
        return trimmed;
      }

      // 截断并添加省略标记，让读者知道消息被截断
      return trimmed.substring(0, maxLength - 3) + "...";
    }

    /// 记录租约获取失败 (并发竞争)
    ///
    /// 场景: 另一个中继实例首先获取了租约
    ///
    /// @param message 未能获取租约的 Outbox 消息
    /// @param leaseOwner 尝试获取租约的实例标识符
    /// @param startTime 中继尝试开始时间戳
    public void recordLeaseMissed(OutboxMessage message, String leaseOwner, Instant startTime) {
      Instant completedAt = Instant.now(clock);
      logs.add(
          createBaseBuilder(message, leaseOwner, startTime)
              .relayStatus(RelayStatus.LEASE_MISSED)
              .completedAt(completedAt)
              .durationMs(computeDuration(startTime, completedAt))
              .build());
    }

    /// 记录成功的消息发布
    ///
    /// 场景: 消息成功发送到下游代理
    ///
    /// @param message 已发布的 Outbox 消息
    /// @param leaseOwner 发布消息的实例标识符
    /// @param startTime 中继尝试开始时间戳
    /// @param publishedAt 确认消息已发布的时间戳
    public void recordPublished(
        OutboxMessage message, String leaseOwner, Instant startTime, Instant publishedAt) {
      logs.add(
          createBaseBuilder(message, leaseOwner, startTime)
              .relayStatus(RelayStatus.PUBLISHED)
              .completedAt(publishedAt)
              .durationMs(computeDuration(startTime, publishedAt))
              .build());
    }

    /// 记录延迟重试 (暂时性错误)
    ///
    /// 场景: 发布失败并出现可重试错误,将在退避后重试
    ///
    /// @param message 遇到暂时性错误的 Outbox 消息
    /// @param leaseOwner 尝试发布的实例标识符
    /// @param startTime 中继尝试开始时间戳
    /// @param nextRetryAt 下次重试尝试的计划时间戳
    /// @param errorCode 错误分类代码 (例如 NETWORK_TIMEOUT)
    /// @param errorMessage 详细的错误消息 (将被截断为 512 个字符)
    /// @param errorKind 错误类型: FATAL 或 TRANSIENT
    public void recordDeferred(
        OutboxMessage message,
        String leaseOwner,
        Instant startTime,
        Instant nextRetryAt,
        String errorCode,
        String errorMessage,
        String errorKind) {
      Instant completedAt = Instant.now(clock);
      logs.add(
          createBaseBuilder(message, leaseOwner, startTime)
              .relayStatus(RelayStatus.DEFERRED)
              .errorCode(errorCode)
              .errorMessage(truncateErrorMessage(errorMessage, 512))
              .errorKind(errorKind)
              .nextRetryAt(nextRetryAt)
              .completedAt(completedAt)
              .durationMs(computeDuration(startTime, completedAt))
              .build());
    }

    /// 记录永久失败 (达到最大重试次数或致命错误)
    ///
    /// 场景: 发布永久失败,不再重试
    ///
    /// @param message 永久失败的 Outbox 消息
    /// @param leaseOwner 尝试发布的实例标识符
    /// @param startTime 中继尝试开始时间戳
    /// @param errorCode 错误分类代码
    /// @param errorMessage 详细的错误消息 (将被截断为 512 个字符)
    /// @param errorKind 错误类型: FATAL 或 TRANSIENT
    public void recordFailed(
        OutboxMessage message,
        String leaseOwner,
        Instant startTime,
        String errorCode,
        String errorMessage,
        String errorKind) {
      Instant completedAt = Instant.now(clock);
      logs.add(
          createBaseBuilder(message, leaseOwner, startTime)
              .relayStatus(RelayStatus.FAILED)
              .errorCode(errorCode)
              .errorMessage(truncateErrorMessage(errorMessage, 512))
              .errorKind(errorKind)
              .completedAt(completedAt)
              .durationMs(computeDuration(startTime, completedAt))
              .build());
    }

    /// 检查累加器是否没有日志
    ///
    /// @return 如果尚未记录日志则返回 true
    public boolean isEmpty() {
      return logs.isEmpty();
    }

    /// 返回收集的日志 (供协调器内部使用)
    ///
    /// @return 收集的中继日志的不可修改视图
    List<OutboxRelayLog> getLogs() {
      return List.copyOf(logs);
    }

    /// 返回累积的日志数量
    ///
    /// @return 中继日志计数
    public int size() {
      return logs.size();
    }
  }
}
