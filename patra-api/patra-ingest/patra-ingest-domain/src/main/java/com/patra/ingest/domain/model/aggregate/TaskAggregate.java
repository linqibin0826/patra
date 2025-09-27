package com.patra.ingest.domain.model.aggregate;

import com.patra.common.domain.AggregateRoot;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.model.event.TaskQueuedEvent;
import com.patra.ingest.domain.model.vo.ExecutionTimeline;
import com.patra.ingest.domain.model.vo.LeaseInfo;
import com.patra.ingest.domain.model.vo.TaskSchedulerContext;

import java.time.Instant;

/**
 * 采集任务聚合根，封装调度、执行、租约等状态信息。
 * <p>支持幂等键生成、运行状态管理以及重试补偿。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class TaskAggregate extends AggregateRoot<Long> {

    /** 调度实例 ID */
    private Long scheduleInstanceId;
    /** 计划 ID */
    private Long planId;
    /** 切片 ID */
    private Long sliceId;
    /** 来源编码 */
    private final String provenanceCode;
    /** 操作编码 */
    private final String operationCode;
    /** 任务参数 JSON */
    private final String paramsJson;
    /** 幂等键 */
    private final String idempotentKey;
    /** 表达式哈希 */
    private final String exprHash;
    /** 调度优先级 */
    private final Integer priority;
    /** 计划执行时间 */
    private final Instant scheduledAt;
    /** 最近心跳时间 */
    private final Instant lastHeartbeatAt;
    /** 重试次数 */
    private final Integer retryCount;
    /** 最近错误码 */
    private final String lastErrorCode;
    /** 最近错误信息 */
    private final String lastErrorMsg;
    /** 任务状态 */
    private TaskStatus status;
    /** 租约信息 */
    private LeaseInfo leaseInfo;
    /** 执行时间线 */
    private ExecutionTimeline executionTimeline;
    /** 调度上下文 */
    private TaskSchedulerContext schedulerContext;

    private TaskAggregate(Long id,
                          Long scheduleInstanceId,
                          Long planId,
                          Long sliceId,
                          String provenanceCode,
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
        this.provenanceCode = provenanceCode;
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
        this.executionTimeline = executionTimeline == null ? ExecutionTimeline.empty() : executionTimeline;
        this.schedulerContext = schedulerContext == null ? TaskSchedulerContext.empty() : schedulerContext;
    }

    /**
     * 创建新的任务聚合。
     *
     * @param scheduleInstanceId 调度实例 ID
     * @param planId 计划 ID
     * @param sliceId 切片 ID 占位
     * @param provenanceCode 来源编码
     * @param operationCode 操作编码
     * @param paramsJson 参数 JSON
     * @param idempotentKey 幂等键
     * @param exprHash 表达式哈希
     * @param priority 优先级
     * @param scheduledAt 计划执行时间
     * @return 新任务聚合
     */
    public static TaskAggregate create(Long scheduleInstanceId,
                                       Long planId,
                                       Long sliceId,
                                       String provenanceCode,
                                       String operationCode,
                                       String paramsJson,
                                       String idempotentKey,
                                       String exprHash,
                                       Integer priority,
                                       Instant scheduledAt) {
        return new TaskAggregate(null,
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

    /**
     * 从持久化记录重建任务聚合。
     */
    public static TaskAggregate restore(Long id,
                                        Long scheduleInstanceId,
                                        Long planId,
                                        Long sliceId,
                                        String provenanceCode,
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
        TaskAggregate aggregate = new TaskAggregate(id,
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

    public void bindPlanAndSlice(Long planId, Long sliceId) {
        this.planId = planId;
        this.sliceId = sliceId;
    }

    /**
     * 聚合准备入队时生成领域事件，供应用层出箱。
     */
    public TaskQueuedEvent raiseQueuedEvent() {
        TaskQueuedEvent event = TaskQueuedEvent.of(
                getId(),
                this.planId,
                this.sliceId,
                this.scheduleInstanceId,
                this.provenanceCode,
                this.operationCode,
                this.idempotentKey,
                this.paramsJson,
                this.priority,
                this.scheduledAt);
        addDomainEvent(event);
        return event;
    }

    public void markQueued() {
        this.status = TaskStatus.QUEUED;
    }

    public void markRunning(Instant startedAt, String schedulerRunId, String correlationId) {
        this.executionTimeline = executionTimeline.onStart(startedAt);
        this.schedulerContext = schedulerContext.withSchedulerRun(schedulerRunId).withCorrelation(correlationId);
        this.status = TaskStatus.RUNNING;
    }

    public void markSucceeded(Instant finishedAt) {
        this.executionTimeline = executionTimeline.onFinish(finishedAt);
        this.status = TaskStatus.SUCCEEDED;
    }

    public void markFailed(Instant finishedAt) {
        this.executionTimeline = executionTimeline.onFinish(finishedAt);
        this.status = TaskStatus.FAILED;
    }

    public void markCancelled(Instant finishedAt) {
        this.executionTimeline = executionTimeline.onFinish(finishedAt);
        this.status = TaskStatus.CANCELLED;
    }

    public void acquireLease(String owner, Instant leasedUntil) {
        this.leaseInfo = leaseInfo.acquire(owner, leasedUntil);
    }

    public void renewLease(String owner, Instant leasedUntil) {
        this.leaseInfo = leaseInfo.renew(owner, leasedUntil);
    }

    public void releaseLease() {
        this.leaseInfo = leaseInfo.release();
    }

    /**
     * 在补偿场景下重置任务状态，回退到排队态。
     */
    public void prepareForRetry() {
        releaseLease();
        this.executionTimeline = ExecutionTimeline.empty();
        this.schedulerContext = TaskSchedulerContext.empty();
        markQueued();
    }

    public LeaseInfo getLeaseInfo() {
        return leaseInfo;
    }

    public ExecutionTimeline getExecutionTimeline() {
        return executionTimeline;
    }

    public TaskSchedulerContext getSchedulerContext() {
        return schedulerContext;
    }

    public Long getScheduleInstanceId() {
        return scheduleInstanceId;
    }

    public Long getPlanId() {
        return planId;
    }

    public Long getSliceId() {
        return sliceId;
    }

    public String getProvenanceCode() {
        return provenanceCode;
    }

    public String getOperationCode() {
        return operationCode;
    }

    public String getParamsJson() {
        return paramsJson;
    }

    public String getIdempotentKey() {
        return idempotentKey;
    }

    public String getExprHash() {
        return exprHash;
    }

    public Integer getPriority() {
        return priority;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public String getLastErrorMsg() {
        return lastErrorMsg;
    }
}
