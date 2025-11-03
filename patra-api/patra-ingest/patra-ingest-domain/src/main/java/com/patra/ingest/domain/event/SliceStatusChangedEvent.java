package com.patra.ingest.domain.event;

import com.patra.common.domain.DomainEvent;
import java.time.Instant;

/**
 * 切片状态变更领域事件,当 Slice 的状态因 Task 完成而变更时触发。
 *
 * <p>触发条件:在基于所有子 Task 的状态重新计算 Slice 的状态后触发。
 *
 * <p>用途:
 *
 * <ul>
 *   <li>聚合:触发基于所有子 Slice 的父 Plan 状态重新计算
 *   <li>指标:按数据源测量切片完成率、成功率和失败率
 *   <li>审计:跟踪切片从创建到完成的生命周期
 *   <li>监控:对高切片失败率或卡住的切片发出告警
 * </ul>
 *
 * <p>幂等性:{@code sliceId} + {@code newStatus} 作为复合键。处理程序必须检查状态是否已被处理。
 *
 * <p>事件链:TaskCompletedEvent → SliceStatusChangedEvent → PlanAggregate 状态更新。
 */
public record SliceStatusChangedEvent(
    /** 切片的主标识符。 */
    Long sliceId,
    /** 拥有该切片的计划标识符。 */
    Long planId,
    /** 变更前的切片旧状态。 */
    String oldStatus,
    /** 聚合后的切片新状态(EXECUTING, SUCCEEDED, FAILED, PARTIAL)。 */
    String newStatus,
    /** 事件发生的时间戳。 */
    Instant occurredAt)
    implements DomainEvent {

  public SliceStatusChangedEvent {
    // 确保事件时间戳始终被填充。
    occurredAt = occurredAt == null ? Instant.now() : occurredAt;
  }

  /**
   * 工厂方法,创建带有自动填充 {@code occurredAt} 的事件。
   *
   * @param sliceId 切片标识符
   * @param planId 计划标识符
   * @param oldStatus 旧状态
   * @param newStatus 新状态
   * @return 事件实例
   */
  public static SliceStatusChangedEvent of(
      Long sliceId, Long planId, String oldStatus, String newStatus) {
    return new SliceStatusChangedEvent(sliceId, planId, oldStatus, newStatus, Instant.now());
  }
}
