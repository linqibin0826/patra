package com.patra.ingest.app.usecase.plan.publisher;

import com.patra.ingest.domain.outbox.OutboxHeaders;
import java.time.Instant;

/**
 * Task Outbox 消息头
 *
 * <p>包含用于追踪、关联和性能监控的元数据。
 *
 * <h3>头部类别</h3>
 *
 * <ul>
 *   <li><b>调度追踪</b>: scheduleInstanceId、scheduler、schedulerJobId
 *   <li><b>时间追踪</b>: triggeredAt、occurredAt(用于延迟分析)
 * </ul>
 *
 * @param scheduleInstanceId 调度实例标识符,用于关联
 * @param scheduler 调度器类型(例如 MANUAL、XXL_JOB)
 * @param schedulerJobId 调度器作业标识符(如不适用则为 null)
 * @param triggeredAt 调度触发时间戳
 * @param occurredAt 事件发生时间戳
 * @author linqibin
 * @since 0.1.0
 */
public record TaskHeaders(
    Long scheduleInstanceId,
    String scheduler,
    String schedulerJobId,
    Instant triggeredAt,
    Instant occurredAt)
    implements OutboxHeaders {

  /**
   * 创建包含所有字段的 TaskHeaders
   *
   * @param scheduleInstanceId 调度实例 ID(必需)
   * @param scheduler 调度器名称(必需)
   * @param schedulerJobId 调度器作业 ID(可空)
   * @param triggeredAt 触发时间戳(必需)
   * @param occurredAt 发生时间戳(必需)
   */
  public TaskHeaders {
    // 紧凑构造函数 - 如需要可在此添加验证
  }
}
