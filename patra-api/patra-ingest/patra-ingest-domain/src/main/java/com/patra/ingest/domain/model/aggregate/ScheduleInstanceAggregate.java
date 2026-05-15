package com.patra.ingest.domain.model.aggregate;

import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.domain.model.vo.schedule.ScheduleInstanceId;
import dev.linqibin.commons.domain.AggregateRoot;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 调度实例聚合根。表示一次调度触发及其初始快照。
///
/// 一致性边界：
///
/// - 调度实例记录单次调度触发的完整上下文
///   - 触发参数在整个生命周期中保持不可变
///
/// 业务规则：
///
/// - 每次调度器触发时创建一个调度实例
///   - 调度实例与计划聚合根是 1:N 关系（一次触发可产生多个计划）
///   - 触发时间戳用于审计和追溯
///
/// 生命周期：
///
/// - 调度器触发时创建
///   - 计划生成时绑定
///   - 全程只读，不参与业务状态流转
///
/// @author linqibin
/// @since 0.1.0
@EqualsAndHashCode(callSuper = true)
@Data
public class ScheduleInstanceAggregate extends AggregateRoot<ScheduleInstanceId> {

  /// 调度器类型（如：XXL_JOB、SPRING_SCHEDULER）。
  private final Scheduler scheduler;

  /// 调度器任务标识。
  private final String schedulerJobId;

  /// 调度器日志标识。
  private final String schedulerLogId;

  /// 触发类型（如：MANUAL、CRON、API）。
  private final TriggerType triggerType;

  /// 触发时间戳。
  private final Instant triggeredAt;

  /// 数据来源代码。
  private final ProvenanceCode provenanceCode;

  /// 调度器传递的触发参数。
  private final Map<String, Object> triggerParams;

  private ScheduleInstanceAggregate(
      ScheduleInstanceId id,
      Scheduler scheduler,
      String schedulerJobId,
      String schedulerLogId,
      TriggerType triggerType,
      Instant triggeredAt,
      Map<String, Object> triggerParams,
      ProvenanceCode provenanceCode) {
    super(id);
    this.scheduler = Objects.requireNonNull(scheduler, "schedulerCode must not be null");
    this.schedulerJobId = schedulerJobId;
    this.schedulerLogId = schedulerLogId;
    this.triggerType = Objects.requireNonNull(triggerType, "triggerType must not be null");
    this.triggeredAt = triggeredAt == null ? Instant.now() : triggeredAt;
    this.triggerParams = triggerParams;
    this.provenanceCode = provenanceCode;
    // Expression prototypes are no longer stored at the scheduling layer; plan aggregates keep them
    // instead.
  }

  public static ScheduleInstanceAggregate start(
      Scheduler scheduler,
      String schedulerJobId,
      String schedulerLogId,
      TriggerType triggerType,
      Instant triggeredAt,
      Map<String, Object> triggerParams,
      ProvenanceCode provenanceCode) {
    return new ScheduleInstanceAggregate(
        null,
        scheduler,
        schedulerJobId,
        schedulerLogId,
        triggerType,
        triggeredAt,
        triggerParams,
        provenanceCode);
  }

  public static ScheduleInstanceAggregate restore(
      ScheduleInstanceId id,
      Scheduler scheduler,
      String schedulerJobId,
      String schedulerLogId,
      TriggerType triggerType,
      Instant triggeredAt,
      Map<String, Object> triggerParams,
      ProvenanceCode provenanceCode,
      long version) {
    ScheduleInstanceAggregate aggregate =
        new ScheduleInstanceAggregate(
            id,
            scheduler,
            schedulerJobId,
            schedulerLogId,
            triggerType,
            triggeredAt,
            triggerParams,
            provenanceCode);
    aggregate.assignVersion(version);
    return aggregate;
  }

  /// 为此调度实例记录额外快照。
  ///
  /// 为未来快照记录需求预留的占位符。
  public void recordSnapshots() {}
}
