package dev.linqibin.patra.ingest.infra.adapter.persistence.entity;

import dev.linqibin.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// 发件箱中继日志 JPA 实体，映射到表 `ing_outbox_relay_log`。
///
/// 表结构: 记录发件箱消息的每次中继执行尝试，为故障排查、性能分析和合规性提供完整的审计跟踪。
///
/// 关键特性:
///
/// - **不可变**: 日志创建后永不更新（仅追加审计跟踪）
/// - **完整**: 捕获调试所需的所有信息（时间、错误、上下文）
/// - **索引优化**: 针对常见查询模式优化（按消息、按批次、按时间范围）
///
/// 使用场景:
///
/// - 历史跟踪: 查询特定消息的完整中继历史
/// - 性能分析: 分析中继耗时并识别瓶颈
/// - 错误分析: 识别错误模式和重试有效性
/// - 批次统计: 按批次聚合成功率和性能指标
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "ing_outbox_relay_log",
    indexes = {
      @Index(name = "idx_relay_log_msg", columnList = "message_id"),
      @Index(name = "idx_relay_log_batch", columnList = "relay_batch_id"),
      @Index(name = "idx_relay_log_status", columnList = "relay_status"),
      @Index(name = "idx_relay_log_time", columnList = "started_at"),
      @Index(name = "idx_relay_log_channel", columnList = "channel, partition_key")
    })
public class OutboxRelayLogEntity extends BaseJpaEntity {

  /// 发件箱消息 ID 引用
  @Column(name = "message_id", nullable = false)
  private Long messageId;

  /// 中继批次标识符（格式: yyyyMMddHHmmss-xxxxxxxx）
  @Column(name = "relay_batch_id", length = 32)
  private String relayBatchId;

  /// 消息通道（反规范化）
  @Column(name = "channel", nullable = false, length = 64)
  private String channel;

  /// 分区键（反规范化）
  @Column(name = "partition_key", length = 128)
  private String partitionKey;

  /// 租约拥有者标识符（格式: host-jobId-threadId-uuid）
  @Column(name = "lease_owner", length = 128)
  private String leaseOwner;

  /// 此消息的尝试次数（从 1 开始）
  @Column(name = "attempt_number", nullable = false)
  private Integer attemptNumber;

  /// 中继执行结果状态（PUBLISHED/DEFERRED/FAILED/LEASE_MISSED）
  @Column(name = "relay_status", nullable = false, length = 32)
  private String relayStatus;

  /// 中继失败时的错误代码（成功时为 NULL）
  @Column(name = "error_code", length = 64)
  private String errorCode;

  /// 中继失败时的错误详情（成功时为 NULL，截断到 512 字符）
  @Column(name = "error_message", length = 512)
  private String errorMessage;

  /// 错误分类: FATAL 或 TRANSIENT（成功时为 NULL）
  @Column(name = "error_kind", length = 32)
  private String errorKind;

  /// 中继开始时间戳（UTC）
  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  /// 中继完成时间戳（UTC）
  @Column(name = "completed_at")
  private Instant completedAt;

  /// 中继执行耗时（毫秒）
  @Column(name = "duration_ms")
  private Integer durationMs;

  /// 下次重试时间戳（UTC），仅在 DEFERRED 状态时存在
  @Column(name = "next_retry_at")
  private Instant nextRetryAt;
}
