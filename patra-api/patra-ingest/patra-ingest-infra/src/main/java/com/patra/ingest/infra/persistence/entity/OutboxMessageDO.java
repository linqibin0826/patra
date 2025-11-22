package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 发件箱消息数据库实体,映射到表 `ing_outbox_message`。
/// 
/// 表结构:
/// 通用出站消息,与业务数据在**同一事务**中持久化(任务通知/集成事件)。中继器仅扫描此表并发布到外部通道(MQ/Webhook),避免热点业务表,确保最小写入侵入和解耦发布。
/// 
/// 关键规则:
/// 
/// - 幂等性: (`channel`, `dedup_key`) 具有唯一约束(UK: uk_outbox_channel_dedup),实现源端去重和安全重试
///   - 排序/分区: `partition_key` 建议格式为 "`provenance:operation`",由索引 `idx_outbox_partition(channel, partition_key, status_code)` 利用以控制并行度和保序(如 `PUBMED:HARVEST`)
///   - 调度/延迟: `not_before` 是最早发布时间(UTC);NULL 表示随时可发布。中继器通常通过 `status_code` +
///       时间游标(`idx_outbox_status_time`)扫描
///   - 租约: `pub_lease_owner`/`pub_leased_until` 防止并发中继器处理同一行;过期后,另一发布者可接管(`idx_outbox_lease`)
/// 
/// 建议状态机: `PENDING → PUBLISHING → PUBLISHED`;失败进入 `FAILED`。基于退避策略,计算 `next_retry_at` 以返回 `PENDING`,或根据策略标记为 `DEAD`。
/// 
/// 字段说明: JSON 列(`payload_json`/`headers_json`)使用 {@link
/// com.patra.starter.mybatis.type.JsonToJsonNodeTypeHandler JsonToJsonNodeTypeHandler} 配合 Jackson
/// {@link com.fasterxml.jackson.databind.JsonNode JsonNode} 进行无模式存储。仅存储**最小必要**信息(如
/// taskId/sliceKey/planKey/provenance/operation/endpoint/priority/notBefore);勿入队大型原始内容。
/// 
/// 审计/公共字段(如 `created_at`/`version`)继承自 {@link BaseDO BaseDO}。
/// 
/// 分层说明: 六边形架构中的*基础设施/持久化 DO*;不含领域行为。
/// 
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_outbox_message", autoResultMap = true)
public class OutboxMessageDO extends BaseDO {

  /// 聚合类型(如 TASK/PLAN/...)。
/// 
/// 用于审计/重放和下游分组。
/// 
/// 约束: NOT NULL;建议使用受控字典中的值。
  @TableField("aggregate_type")
  private String aggregateType;

  /// 聚合根 ID。
/// 
/// 任务场景: 等于 `ing_task.id`;其他聚合使用自己的主键。
/// 
/// 用作重放/对账/诊断的主要关联键。
  @TableField("aggregate_id")
  private Long aggregateId;

  /// 逻辑通道 = 目标主题(如 `INGEST_TASK`)。
/// 
/// 与 {@link #dedupKey} 组合形成唯一键(UK: uk_outbox_channel_dedup)。
/// 
/// 建议: 保持通道分类稳定且有限;路由/认证由适配器处理。
  @TableField("channel")
  private String channel;

  /// 语义操作标签,如 `TASK_READY`、`EVENT_PUBLISHED`。
/// 
/// 供订阅者用于路由/指标;不是幂等键的一部分。
  @TableField("op_type")
  private String opType;

  /// 分区/排序路由键。
/// 
/// 建议格式: `"provenance:operation"`,如 `PUBMED:HARVEST`。
/// 
/// 通过索引 `idx_outbox_partition(channel, partition_key, status_code)` 控制同一 `channel`
/// 下的分区并发和有序发布。
/// 
/// 注意: 不是唯一性约束的一部分;独立于 {@link #dedupKey}。
  @TableField("partition_key")
  private String partitionKey;

  /// 去重键。
/// 
/// 唯一性约束: 在同一 {@link #channel} 内必须唯一(UK: uk_outbox_channel_dedup)。
/// 
/// 任务场景: 建议等于 `ing_task.idempotent_key`;其他场景可使用 `requestId` 或内容哈希。
/// 
/// 目的: 源端去重和安全重试;即使消费者接收到重复消息,源端也会抑制它们。
  @TableField("dedup_key")
  private String dedupKey;

  /// 最小载荷(JSON)。
/// 
/// 典型字段: `taskId`/`sliceKey`/`planKey`/`provenance`/`operation`/`endpoint`/`priority`/`notBefore`。
/// 
/// 约束: 仅存储发布所需的**最小**信息;不得入队大型/原始文档。
  @TableField("payload_json")
  private JsonNode payloadJson;

  /// 扩展头部(JSON),如 `correlationId`、跟踪上下文。
/// 
/// 可选;用于跨系统关联和故障排查。
  @TableField("headers_json")
  private JsonNode headersJson;

  /// 最早发布时间(UTC)。
/// 
/// NULL = 随时可发布;用于调度/延迟和速率整形。
  @TableField("not_before")
  private Instant notBefore;

  /// 成功发布时间戳(UTC)。
/// 
/// 当状态转换为 PUBLISHED 时设置;所有其他状态为 NULL。
/// 
/// 用例: 跟踪中继延迟(published_at - created_at)、审计跟踪。
/// 
/// @since 0.1.0
  @TableField("published_at")
  private Instant publishedAt;

  /// 发布状态代码。
/// 
/// 值: `PENDING`/`PUBLISHING`/`PUBLISHED`/`FAILED`/`DEAD`。
/// 
/// 扫描策略: 通常按索引批量提取(`status_code`、`not_before`、`id`)。
  @TableField("status_code")
  private String statusCode;

  /// 发布重试计数(失败时递增)。
/// 
/// 与退避策略(指数/斐波那契等)结合使用,计算 {@link #nextRetryAt}。
  @TableField("retry_count")
  private Integer retryCount;

  /// 下次重试发布时间(UTC)。
/// 
/// 当 `now >= next_retry_at` 且状态允许时,中继器可重新获取记录。
  @TableField("next_retry_at")
  private Instant nextRetryAt;

  /// 上次发布错误代码(来自 MQ SDK 或内部策略)
  @TableField("error_code")
  private String errorCode;

  /// 上次发布错误详情(截断)
  @TableField("error_msg")
  private String errorMsg;

  /// 发布者租约拥有者(实例 ID / 工作者 ID)。
/// 
/// 防止多个中继工作者重复发布。
  @TableField("pub_lease_owner")
  private String pubLeaseOwner;

  /// 发布者租约过期时间(UTC)。
/// 
/// 仅租约拥有者可在过期前处理;过期后另一发布者可接管。
  @TableField("pub_leased_until")
  private Instant pubLeasedUntil;
}
