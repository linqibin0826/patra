package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 发件箱中继日志数据库实体,映射到表 `ing_outbox_relay_log`。
///
/// 表结构: 记录发件箱消息的每次中继执行尝试,为故障排查、性能分析和合规性提供完整的审计跟踪。
///
/// 关键特性:
///
/// - **不可变**: 日志创建后永不更新(仅追加审计跟踪)
///   - **完整**: 捕获调试所需的所有信息(时间、错误、上下文)
///   - **索引优化**: 针对常见查询模式优化(按消息、按批次、按时间范围)
///
/// 使用场景:
///
/// - 历史跟踪: 查询特定消息的完整中继历史
///   - 性能分析: 分析中继耗时并识别瓶颈
///   - 错误分析: 识别错误模式和重试有效性
///   - 批次统计: 按批次聚合成功率和性能指标
///   - 监控: 对失败中继或性能下降的实时告警
///
/// 与 {@link OutboxMessageDO} 的关系:
///
/// - 一个发件箱消息可以有多个中继日志条目(每次尝试一个)
///   - 引用完整性在**应用层**强制执行(为性能考虑,无数据库 FK)
///   - 从发件箱消息反规范化 `channel` 和 `partitionKey` 以提高查询效率
///
/// 中继状态值:
///
/// - `PUBLISHED`: 成功发布到下游代理(终态)
///   - `DEFERRED`: 可重试错误失败,已安排重试(瞬态)
///   - `FAILED`: 达到最大重试次数或致命错误后永久失败(终态)
///   - `LEASE_MISSED`: 由于并发竞争未能获取租约
///
/// 审计/公共字段(如 `created_at`、`version`、`deleted`)继承自 {@link BaseDO BaseDO}。
///
/// 分层说明: 六边形架构中的*基础设施/持久化 DO*;不含领域行为。
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_outbox_relay_log", autoResultMap = true)
public class OutboxRelayLogDO extends BaseDO {

  /// 发件箱消息 ID 引用。
  ///
  /// 逻辑外键到 {@link OutboxMessageDO#getId() ing_outbox_message.id};完整性在应用层强制执行。
  ///
  /// 约束: NOT NULL;必须引用现有的发件箱消息。
  @TableField("message_id")
  private Long messageId;

  /// 中继批次标识符(格式: yyyyMMddHHmmss-xxxxxxxx)。
  ///
  /// 将来自同一作业执行的所有中继日志分组(如调度的中继作业运行)。
  ///
  /// 示例: `20251031150000-a1b2c3d4`
  ///
  /// 使用场景:
  ///
  /// - 批次级统计(成功率、平均耗时)
  ///   - 批次失败故障排查
  ///   - 每批次性能分析
  ///
  @TableField("relay_batch_id")
  private String relayBatchId;

  /// 消息通道(从 {@link OutboxMessageDO#getChannel()} 反规范化)。
  ///
  /// 反规范化以提高查询效率(避免与 outbox_message 表 JOIN)。
  ///
  /// 示例: `INGEST_TASK`、`REGISTRY_UPDATE`
  @TableField("channel")
  private String channel;

  /// 分区键(从 {@link OutboxMessageDO#getPartitionKey()} 反规范化)。
  ///
  /// 用于分析和排查分区特定问题。
  ///
  /// 示例: `PUBMED:HARVEST`
  @TableField("partition_key")
  private String partitionKey;

  /// 租约拥有者标识符(格式: host-jobId-threadId-uuid)。
  ///
  /// 标识哪个实例/线程尝试了此中继(用于分布式故障排查)。
  ///
  /// 示例: `ingest-server-1-job123-thread456-a1b2c3d4`
  @TableField("lease_owner")
  private String leaseOwner;

  /// 此消息的尝试次数(从 1 开始)。
  ///
  /// 首次尝试 = 1,重试时递增。
  ///
  /// 使用场景:
  ///
  /// - 重试分析(成功前尝试了多少次?)
  ///   - 性能分析(后续尝试是否耗时更长?)
  ///   - 告警(尝试次数 > 阈值时触发告警)
  ///
  @TableField("attempt_number")
  private Integer attemptNumber;

  /// 中继执行结果状态。
  ///
  /// 值:
  ///
  /// - `PUBLISHED`: 成功(终态)
  ///   - `DEFERRED`: 瞬态错误,将重试(瞬态)
  ///   - `FAILED`: 永久失败(终态)
  ///   - `LEASE_MISSED`: 并发租约冲突(瞬态)
  ///
  /// 已建立索引,用于告警查询(如"显示最近一小时内所有 FAILED 中继")。
  @TableField("relay_status")
  private String relayStatus;

  /// 中继失败时的错误代码(成功时为 NULL)。
  ///
  /// 示例: `NETWORK_TIMEOUT`、`BROKER_UNAVAILABLE`、`SERIALIZATION_ERROR`
  ///
  /// 使用场景:
  ///
  /// - 错误分布分析(哪些错误最常见?)
  ///   - 根因分析(按错误代码分组失败)
  ///   - 告警路由(针对不同错误类型发送不同告警)
  ///
  @TableField("error_code")
  private String errorCode;

  /// 中继失败时的错误详情(成功时为 NULL,截断到 512 字符)。
  ///
  /// 包含详细错误消息和堆栈跟踪摘录。
  ///
  /// 注意: 应用层截断到 512 字符以防止过度存储。
  @TableField("error_message")
  private String errorMessage;

  /// 错误分类: FATAL 或 TRANSIENT(成功时为 NULL)。
  ///
  /// 值:
  ///
  /// - `FATAL`: 不可重试错误(如无效载荷、认证失败)
  ///   - `TRANSIENT`: 可重试错误(如网络超时、代理不可用)
  ///
  /// 用于重试决策和告警。
  @TableField("error_kind")
  private String errorKind;

  /// 中继开始时间戳(UTC)。
  ///
  /// 标记中继执行开始时间(租约获取之前)。
  ///
  /// 已建立索引,用于时间范围查询(如"显示最近 24 小时内的所有中继")。
  @TableField("started_at")
  private Instant startedAt;

  /// 中继完成时间戳(UTC)。
  ///
  /// 标记中继执行完成时间(发布或错误处理之后)。
  @TableField("completed_at")
  private Instant completedAt;

  /// 中继执行耗时(毫秒)(completedAt - startedAt)。
  ///
  /// 使用场景:
  ///
  /// - 性能分析(P50、P95、P99 延迟)
  ///   - 瓶颈识别(哪些消息耗时最长?)
  ///   - SLA 监控(耗时 > 阈值时触发告警)
  ///
  @TableField("duration_ms")
  private Integer durationMs;

  /// 下次重试时间戳(UTC),仅在 DEFERRED 状态时存在。
  ///
  /// 所有其他状态(PUBLISHED、FAILED、LEASE_MISSED)为 NULL。
  ///
  /// 基于尝试次数使用指数退避策略计算。
  @TableField("next_retry_at")
  private Instant nextRetryAt;
}
