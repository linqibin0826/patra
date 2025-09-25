package com.patra.ingest.domain.model.aggregate;

import com.patra.common.domain.AggregateRoot;
import com.patra.ingest.domain.model.enums.TaskStatus;

import java.time.Instant;

/**
 * 采集任务聚合根。
 */
public class TaskAggregate extends AggregateRoot<Long> {

    private Long scheduleInstanceId;
    private Long planId;
    private Long sliceId;
    private final String provenanceCode;
    private final String operationCode;
    private final String paramsJson;
    private final String idempotentKey;
    private final String exprHash;
    private final Integer priority;
    private final Instant scheduledAt;
    private final Instant lastHeartbeatAt;
    private final Integer retryCount;
    private final String lastErrorCode;
    private final String lastErrorMsg;
    private TaskStatus status;

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
                          TaskStatus status) {
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
    }

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
                TaskStatus.QUEUED);
    }

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
                status);
        aggregate.assignVersion(version);
        return aggregate;
    }

    public void bindPlanAndSlice(Long planId, Long sliceId) {
        this.planId = planId;
        this.sliceId = sliceId;
    }

    public void markQueued() {
        this.status = TaskStatus.QUEUED;
    }

    public void markRunning() {
        this.status = TaskStatus.RUNNING;
    }

    public void markSucceeded() {
        this.status = TaskStatus.SUCCEEDED;
    }

    public void markFailed() {
        this.status = TaskStatus.FAILED;
    }

    public void markCancelled() {
        this.status = TaskStatus.CANCELLED;
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
