package com.patra.ingest.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/// 发件箱消息 JPA 实体，映射到表 `ing_outbox_message`。
///
/// 表结构:
/// 通用出站消息，与业务数据在**同一事务**中持久化（任务通知/集成事件）。
/// 中继器仅扫描此表并发布到外部通道（MQ/Webhook），避免热点业务表，确保最小写入侵入和解耦发布。
///
/// 关键规则:
///
/// - 幂等性: (`channel`, `dedup_key`) 具有唯一约束（UK: uk_outbox_channel_dedup），实现源端去重和安全重试
/// - 排序/分区: `partition_key` 建议格式为 "`provenance:operation`"，用于控制并行度和保序
/// - 调度/延迟: `not_before` 是最早发布时间（UTC）；NULL 表示随时可发布
/// - 租约: `pub_lease_owner`/`pub_leased_until` 防止并发中继器处理同一行
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "ing_outbox_message",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_outbox_channel_dedup",
          columnNames = {"channel", "dedup_key"})
    },
    indexes = {
      @Index(name = "idx_outbox_partition", columnList = "channel, partition_key, status_code"),
      @Index(name = "idx_outbox_status_time", columnList = "status_code, not_before, id"),
      @Index(name = "idx_outbox_lease", columnList = "pub_lease_owner, pub_leased_until"),
      @Index(name = "idx_outbox_aggregate", columnList = "aggregate_type, aggregate_id")
    })
public class OutboxMessageEntity extends BaseJpaEntity {

  /// 聚合类型（如 TASK/PLAN/...）
  @Column(name = "aggregate_type", nullable = false, length = 32)
  private String aggregateType;

  /// 聚合根 ID
  @Column(name = "aggregate_id", nullable = false)
  private Long aggregateId;

  /// 逻辑通道 = 目标主题（如 `INGEST_TASK`）
  @Column(name = "channel", nullable = false, length = 64)
  private String channel;

  /// 语义操作标签，如 `TASK_READY`、`EVENT_PUBLISHED`
  @Column(name = "op_type", length = 64)
  private String opType;

  /// 分区/排序路由键（建议格式: `"provenance:operation"`）
  @Column(name = "partition_key", length = 128)
  private String partitionKey;

  /// 去重键（在同一 channel 内唯一）
  @Column(name = "dedup_key", nullable = false, length = 128)
  private String dedupKey;

  /// 最小载荷（JSON）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_json", columnDefinition = "JSON")
  private JsonNode payloadJson;

  /// 扩展头部（JSON），如 correlationId、跟踪上下文
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "headers_json", columnDefinition = "JSON")
  private JsonNode headersJson;

  /// 最早发布时间（UTC）；NULL = 随时可发布
  @Column(name = "not_before")
  private Instant notBefore;

  /// 成功发布时间戳（UTC）
  @Column(name = "published_at")
  private Instant publishedAt;

  /// 发布状态代码（PENDING/PUBLISHING/PUBLISHED/FAILED/DEAD）
  @Column(name = "status_code", nullable = false, length = 32)
  private String statusCode;

  /// 发布重试计数（失败时递增）
  @Column(name = "retry_count")
  private Integer retryCount;

  /// 下次重试发布时间（UTC）
  @Column(name = "next_retry_at")
  private Instant nextRetryAt;

  /// 上次发布错误代码
  @Column(name = "error_code", length = 64)
  private String errorCode;

  /// 上次发布错误详情（截断）
  @Column(name = "error_msg", length = 512)
  private String errorMsg;

  /// 发布者租约拥有者（实例 ID / 工作者 ID）
  @Column(name = "pub_lease_owner", length = 128)
  private String pubLeaseOwner;

  /// 发布者租约过期时间（UTC）
  @Column(name = "pub_leased_until")
  private Instant pubLeasedUntil;
}
