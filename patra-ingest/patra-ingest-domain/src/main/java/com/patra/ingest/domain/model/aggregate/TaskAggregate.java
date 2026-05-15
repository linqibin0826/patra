package com.patra.ingest.domain.model.aggregate;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.event.TaskCompletedEvent;
import com.patra.ingest.domain.event.TaskQueuedEvent;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.model.vo.execution.ExecutionTimeline;
import com.patra.ingest.domain.model.vo.plan.PlanId;
import com.patra.ingest.domain.model.vo.plan.TaskSchedulerContext;
import com.patra.ingest.domain.model.vo.schedule.ScheduleInstanceId;
import com.patra.ingest.domain.model.vo.shared.LeaseInfo;
import com.patra.ingest.domain.model.vo.slice.PlanSliceId;
import com.patra.ingest.domain.model.vo.task.TaskId;
import dev.linqibin.commons.domain.AggregateRoot;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

/// 采集任务聚合根。封装数据采集任务的原子工作单元，包含调度元数据、执行进度、租约管理和重试簿记。
///
/// 一致性边界：
///
/// - 任务的参数快照、幂等键在整个生命周期中保持不可变
///   - 租约信息确保分布式环境下的任务互斥执行
///   - 执行时间线记录任务的完整生命周期
///
/// 业务规则：
///
/// - 任务创建时处于 `QUEUED` 状态，等待工作者拉取
///   - 工作者获取租约后转换为 `RUNNING` 状态
///   - 执行成功后转换为 `SUCCEEDED` 状态，发布 {@link TaskCompletedEvent}
///   - 执行失败后转换为 `FAILED` 状态，可根据重试策略重新入队
///   - 租约到期后任务可被其他工作者重新获取
///   - 幂等键 = hash(provenance + operation + params) 确保任务去重
///
/// 状态转换：
///
/// - `QUEUED` → `RUNNING`: 工作者获取租约并开始执行
///   - `RUNNING` → `SUCCEEDED`: 任务执行成功
///   - `RUNNING` → `FAILED`: 任务执行失败
///   - `FAILED` → `QUEUED`: 根据重试策略重新入队
///
/// 领域事件：
///
/// - {@link TaskQueuedEvent}: 任务创建时发布，通知工作者拉取
///   - {@link TaskCompletedEvent}: 任务完成时发布，触发切片和计划状态聚合
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class TaskAggregate extends AggregateRoot<TaskId> {

  /// 调度实例标识。
  private ScheduleInstanceId scheduleInstanceId;

  /// 所属计划标识。
  private PlanId planId;

  /// 所属切片标识。
  private PlanSliceId sliceId;

  /// 数据来源代码（如：pubmed）。
  private final ProvenanceCode provenanceCode;

  /// 操作代码（如：harvest、update）。
  private final String operationCode;

  /// 任务参数载荷（JSON 格式）。
  private final String paramsJson;

  /// 幂等键，用于任务去重。
  private final String idempotentKey;

  /// 表达式哈希值。
  private final String exprHash;

  /// 调度优先级。
  private final Integer priority;

  /// 计划执行时间。
  private final Instant scheduledAt;

  /// 最后心跳时间戳。
  private final Instant lastHeartbeatAt;

  /// 重试次数。
  private final Integer retryCount;

  /// 最近错误代码。
  private final String lastErrorCode;

  /// 最近错误消息。
  private final String lastErrorMsg;

  /// 任务当前状态。
  private TaskStatus status;

  /// 租约所有权元数据（owner + leasedUntil）。
  private LeaseInfo leaseInfo;

  /// 执行时间线标记（startedAt + completedAt）。
  private ExecutionTimeline executionTimeline;

  /// 随任务传播的调度器上下文。
  private TaskSchedulerContext schedulerContext;

  /// 私有构造函数，用于内部对象构建和重建。
  ///
  /// 通过静态工厂方法 {@link #create} 或 {@link #restore} 创建实例。
  ///
  /// @param id 任务标识
  /// @param scheduleInstanceId 调度实例标识
  /// @param planId 所属计划标识
  /// @param sliceId 所属切片标识
  /// @param provenanceCode 数据来源代码
  /// @param operationCode 操作代码
  /// @param paramsJson 任务参数载荷
  /// @param idempotentKey 幂等键
  /// @param exprHash 表达式哈希
  /// @param priority 调度优先级
  /// @param scheduledAt 计划执行时间
  /// @param lastHeartbeatAt 最后心跳时间戳
  /// @param retryCount 重试次数
  /// @param lastErrorCode 最近错误代码
  /// @param lastErrorMsg 最近错误消息
  /// @param status 当前任务状态
  /// @param leaseInfo 租约信息
  /// @param executionTimeline 执行时间线
  /// @param schedulerContext 调度器上下文
  private TaskAggregate(
      TaskId id,
      ScheduleInstanceId scheduleInstanceId,
      PlanId planId,
      PlanSliceId sliceId,
      ProvenanceCode provenanceCode,
      String operationCode,
      String paramsJson,
      String idempotentKey,
      String exprHash,
      Integer priority,
      Instant scheduledAt,
      Instant lastHeartbeatAt,
      Integer retryCount,
      String lastErrorCode,
      String lastErrorMsg,
      TaskStatus status,
      LeaseInfo leaseInfo,
      ExecutionTimeline executionTimeline,
      TaskSchedulerContext schedulerContext) {
    super(id);
    this.scheduleInstanceId = scheduleInstanceId;
    this.planId = planId;
    this.sliceId = sliceId;
    this.provenanceCode = Objects.requireNonNull(provenanceCode, "provenanceCode 不能为 null");
    this.operationCode = operationCode;
    this.paramsJson = paramsJson;
    this.idempotentKey = idempotentKey;
    this.exprHash = exprHash;
    this.priority = priority;
    this.scheduledAt = scheduledAt;
    this.lastHeartbeatAt = lastHeartbeatAt;
    this.retryCount = retryCount;
    this.lastErrorCode = lastErrorCode;
    this.lastErrorMsg = lastErrorMsg;
    this.status = status == null ? TaskStatus.QUEUED : status;
    this.leaseInfo = leaseInfo == null ? LeaseInfo.none() : leaseInfo;
    this.executionTimeline =
        executionTimeline == null ? ExecutionTimeline.empty() : executionTimeline;
    this.schedulerContext =
        schedulerContext == null ? TaskSchedulerContext.empty() : schedulerContext;
  }

  /// 创建新的任务聚合根，初始状态为队列中 (QUEUED)。
  ///
  /// @param scheduleInstanceId 调度实例标识
  /// @param planId 所属计划标识
  /// @param sliceId 所属切片标识占位符
  /// @param provenanceCode 数据来源代码
  /// @param operationCode 操作代码
  /// @param paramsJson 任务参数载荷（JSON 格式）
  /// @param idempotentKey 幂等键
  /// @param exprHash 表达式哈希
  /// @param priority 调度优先级
  /// @param scheduledAt 计划执行时间
  /// @return 新创建的任务聚合根，准备持久化
  public static TaskAggregate create(
      ScheduleInstanceId scheduleInstanceId,
      PlanId planId,
      PlanSliceId sliceId,
      ProvenanceCode provenanceCode,
      String operationCode,
      String paramsJson,
      String idempotentKey,
      String exprHash,
      Integer priority,
      Instant scheduledAt) {
    return new TaskAggregate(
        null,
        scheduleInstanceId,
        planId,
        sliceId,
        provenanceCode,
        operationCode,
        paramsJson,
        idempotentKey,
        exprHash,
        priority,
        scheduledAt,
        null,
        0,
        null,
        null,
        TaskStatus.QUEUED,
        LeaseInfo.none(),
        ExecutionTimeline.empty(),
        TaskSchedulerContext.empty());
  }

  /// 从持久化状态重建已存在的任务聚合根。
  ///
  /// @param id 任务标识
  /// @param scheduleInstanceId 调度实例标识
  /// @param planId 所属计划标识
  /// @param sliceId 所属切片标识
  /// @param provenanceCode 数据来源代码
  /// @param operationCode 操作代码
  /// @param paramsJson 任务参数载荷（JSON 格式）
  /// @param idempotentKey 幂等键
  /// @param exprHash 表达式哈希
  /// @param priority 调度优先级
  /// @param scheduledAt 计划执行时间
  /// @param lastHeartbeatAt 最后心跳时间戳
  /// @param retryCount 重试次数
  /// @param lastErrorCode 最近错误代码
  /// @param lastErrorMsg 最近错误消息
  /// @param status 当前任务状态
  /// @param leaseInfo 租约所有权元数据
  /// @param executionTimeline 执行时间线标记
  /// @param schedulerContext 调度器上下文
  /// @param version 乐观锁版本
  /// @return 从持久化重建的任务聚合根
  public static TaskAggregate restore(
      TaskId id,
      ScheduleInstanceId scheduleInstanceId,
      PlanId planId,
      PlanSliceId sliceId,
      ProvenanceCode provenanceCode,
      String operationCode,
      String paramsJson,
      String idempotentKey,
      String exprHash,
      Integer priority,
      Instant scheduledAt,
      Instant lastHeartbeatAt,
      Integer retryCount,
      String lastErrorCode,
      String lastErrorMsg,
      TaskStatus status,
      LeaseInfo leaseInfo,
      ExecutionTimeline executionTimeline,
      TaskSchedulerContext schedulerContext,
      long version) {
    TaskAggregate aggregate =
        createRestoredInstance(
            id,
            scheduleInstanceId,
            planId,
            sliceId,
            provenanceCode,
            operationCode,
            paramsJson,
            idempotentKey,
            exprHash,
            priority,
            scheduledAt,
            lastHeartbeatAt,
            retryCount,
            lastErrorCode,
            lastErrorMsg,
            status,
            leaseInfo,
            executionTimeline,
            schedulerContext);
    aggregate.assignVersion(version);
    return aggregate;
  }

  /// 私有辅助方法，创建重建的任务实例（不分配版本）。
  ///
  /// 用于分离对象构建和版本分配逻辑。
  ///
  /// @param id 任务标识
  /// @param scheduleInstanceId 调度实例标识
  /// @param planId 所属计划标识
  /// @param sliceId 所属切片标识
  /// @param provenanceCode 数据来源代码
  /// @param operationCode 操作代码
  /// @param paramsJson 任务参数载荷
  /// @param idempotentKey 幂等键
  /// @param exprHash 表达式哈希
  /// @param priority 调度优先级
  /// @param scheduledAt 计划执行时间
  /// @param lastHeartbeatAt 最后心跳时间戳
  /// @param retryCount 重试次数
  /// @param lastErrorCode 最近错误代码
  /// @param lastErrorMsg 最近错误消息
  /// @param status 当前任务状态
  /// @param leaseInfo 租约信息
  /// @param executionTimeline 执行时间线
  /// @param schedulerContext 调度器上下文
  /// @return 重建的任务实例（不含版本）
  private static TaskAggregate createRestoredInstance(
      TaskId id,
      ScheduleInstanceId scheduleInstanceId,
      PlanId planId,
      PlanSliceId sliceId,
      ProvenanceCode provenanceCode,
      String operationCode,
      String paramsJson,
      String idempotentKey,
      String exprHash,
      Integer priority,
      Instant scheduledAt,
      Instant lastHeartbeatAt,
      Integer retryCount,
      String lastErrorCode,
      String lastErrorMsg,
      TaskStatus status,
      LeaseInfo leaseInfo,
      ExecutionTimeline executionTimeline,
      TaskSchedulerContext schedulerContext) {
    return new TaskAggregate(
        id,
        scheduleInstanceId,
        planId,
        sliceId,
        provenanceCode,
        operationCode,
        paramsJson,
        idempotentKey,
        exprHash,
        priority,
        scheduledAt,
        lastHeartbeatAt,
        retryCount,
        lastErrorCode,
        lastErrorMsg,
        status,
        leaseInfo,
        executionTimeline,
        schedulerContext);
  }

  /// 在持久化后将任务绑定到特定计划和切片。
  ///
  /// @param planId 计划标识
  /// @param sliceId 切片标识
  public void bindPlanAndSlice(PlanId planId, PlanSliceId sliceId) {
    this.planId = planId;
    this.sliceId = sliceId;
  }

  /// 当任务准备入队时发布领域事件。
  ///
  /// 应用层应将此事件发布到消息队列。
  ///
  /// @return 任务入队事件
  public TaskQueuedEvent raiseQueuedEvent() {
    TaskQueuedEvent event =
        TaskQueuedEvent.of(
            getId() != null ? getId().value() : null,
            this.planId != null ? this.planId.value() : null,
            this.sliceId != null ? this.sliceId.value() : null,
            this.scheduleInstanceId != null ? this.scheduleInstanceId.value() : null,
            this.provenanceCode,
            this.operationCode,
            this.idempotentKey,
            this.paramsJson,
            this.priority,
            this.scheduledAt);
    addDomainEvent(event);
    return event;
  }

  /// 获取数据来源代码字符串值。
  ///
  /// @return 数据来源代码字符串
  public String getProvenanceCodeValue() {
    return provenanceCode.getCode();
  }

  /// 将任务标记为队列中状态。
  public void markQueued() {
    this.status = TaskStatus.QUEUED;
  }

  /// 将任务标记为运行中并记录执行上下文。
  ///
  /// @param startedAt 执行开始时间
  /// @param correlationId 跟踪关联标识
  public void markRunning(Instant startedAt, String correlationId) {
    this.executionTimeline = executionTimeline.onStart(startedAt);
    this.schedulerContext = schedulerContext.withCorrelation(correlationId);
    this.status = TaskStatus.RUNNING;
  }

  /// 将任务标记为执行成功。
  ///
  /// @param finishedAt 执行完成时间
  public void markSucceeded(Instant finishedAt) {
    this.executionTimeline = executionTimeline.onFinish(finishedAt);
    this.status = TaskStatus.SUCCEEDED;

    // 发布领域事件以触发切片和计划状态聚合
    addDomainEvent(
        TaskCompletedEvent.of(
            getId() != null ? getId().value() : null,
            this.sliceId != null ? this.sliceId.value() : null,
            this.planId != null ? this.planId.value() : null,
            TaskStatus.SUCCEEDED.getCode(),
            finishedAt));
  }

  /// 将任务标记为执行失败。
  ///
  /// @param finishedAt 执行完成时间
  public void markFailed(Instant finishedAt) {
    this.executionTimeline = executionTimeline.onFinish(finishedAt);
    this.status = TaskStatus.FAILED;

    // 发布领域事件以触发切片和计划状态聚合
    addDomainEvent(
        TaskCompletedEvent.ofFailure(
            getId() != null ? getId().value() : null,
            this.sliceId != null ? this.sliceId.value() : null,
            this.planId != null ? this.planId.value() : null,
            TaskStatus.FAILED.getCode(),
            this.lastErrorCode,
            this.lastErrorMsg,
            finishedAt));
  }

  /// 为此任务获取租约。
  ///
  /// @param owner 租约所有者标识
  /// @param leasedUntil 租约过期时间
  public void acquireLease(String owner, Instant leasedUntil) {
    this.leaseInfo = leaseInfo.acquire(owner, leasedUntil);
  }

  /// 续约现有租约。
  ///
  /// @param owner 租约所有者标识
  /// @param leasedUntil 新的租约过期时间
  public void renewLease(String owner, Instant leasedUntil) {
    this.leaseInfo = leaseInfo.renew(owner, leasedUntil);
  }

  /// 释放当前租约。
  public void releaseLease() {
    this.leaseInfo = leaseInfo.release();
  }

  /// 通过清除运行时上下文来重置任务以便重试。
  ///
  /// 释放租约、清除执行时间线和调度器上下文，然后标记为队列中状态。
  public void prepareForRetry() {
    releaseLease();
    this.executionTimeline = ExecutionTimeline.empty();
    this.schedulerContext = TaskSchedulerContext.empty();
    markQueued();
  }
}
