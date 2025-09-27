package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.model.vo.ExecutionTimeline;
import com.patra.ingest.domain.model.vo.LeaseInfo;
import com.patra.ingest.domain.model.vo.TaskSchedulerContext;
import com.patra.starter.core.json.JsonNodeSupport;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 任务聚合与数据对象转换（MapStruct 接口）。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskConverter extends JsonNodeSupport {

    default TaskDO toEntity(TaskAggregate aggregate) {
        if (aggregate == null) {
            return null;
        }
        TaskDO entity = new TaskDO();
        entity.setId(aggregate.getId());
        entity.setScheduleInstanceId(aggregate.getScheduleInstanceId());
        entity.setPlanId(aggregate.getPlanId());
        entity.setSliceId(aggregate.getSliceId());
        entity.setProvenanceCode(aggregate.getProvenanceCode());
        entity.setOperationCode(aggregate.getOperationCode());
        entity.setParams(readJsonNode(aggregate.getParamsJson()));
        entity.setIdempotentKey(aggregate.getIdempotentKey());
        entity.setExprHash(aggregate.getExprHash());
        entity.setPriority(aggregate.getPriority());
        entity.setScheduledAt(aggregate.getScheduledAt());
        entity.setLastHeartbeatAt(aggregate.getLastHeartbeatAt());
        entity.setRetryCount(aggregate.getRetryCount());
        entity.setLastErrorCode(aggregate.getLastErrorCode());
        entity.setLastErrorMsg(aggregate.getLastErrorMsg());
        LeaseInfo leaseInfo = aggregate.getLeaseInfo();
        if (leaseInfo != null) {
            entity.setLeaseOwner(leaseInfo.owner());
            entity.setLeasedUntil(leaseInfo.leasedUntil());
            entity.setLeaseCount(leaseInfo.leaseCount());
        }
        ExecutionTimeline timeline = aggregate.getExecutionTimeline();
        if (timeline != null) {
            entity.setStartedAt(timeline.startedAt());
            entity.setFinishedAt(timeline.finishedAt());
        }
        TaskSchedulerContext schedulerContext = aggregate.getSchedulerContext();
        if (schedulerContext != null) {
            entity.setSchedulerRunId(schedulerContext.schedulerRunId());
            entity.setCorrelationId(schedulerContext.correlationId());
        }
        // 使用字典编码
        entity.setStatusCode(aggregate.getStatus() == null ? null : aggregate.getStatus().getCode());
        entity.setVersion(aggregate.getVersion());
        return entity;
    }

    default TaskAggregate toAggregate(TaskDO entity) {
        if (entity == null) {
            return null;
        }
        TaskStatus status = entity.getStatusCode() == null
                ? TaskStatus.QUEUED
                : TaskStatus.fromCode(entity.getStatusCode());
        long version = entity.getVersion() == null ? 0L : entity.getVersion();
        String paramsJson = writeJsonString(entity.getParams());
        LeaseInfo leaseInfo = LeaseInfo.snapshotOf(entity.getLeaseOwner(), entity.getLeasedUntil(), entity.getLeaseCount());
        ExecutionTimeline timeline = new ExecutionTimeline(entity.getStartedAt(), entity.getFinishedAt());
        TaskSchedulerContext schedulerContext = new TaskSchedulerContext(entity.getSchedulerRunId(), entity.getCorrelationId());
        return TaskAggregate.restore(
                entity.getId(),
                entity.getScheduleInstanceId(),
                entity.getPlanId(),
                entity.getSliceId(),
                entity.getProvenanceCode(),
                entity.getOperationCode(),
                paramsJson,
                entity.getIdempotentKey(),
                entity.getExprHash(),
                entity.getPriority(),
                entity.getScheduledAt(),
                entity.getLastHeartbeatAt(),
                entity.getRetryCount(),
                entity.getLastErrorCode(),
                entity.getLastErrorMsg(),
                status,
                leaseInfo,
                timeline,
                schedulerContext,
                version);
    }
}
