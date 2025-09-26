package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 任务聚合与数据对象转换（MapStruct 接口）。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskConverter {

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
        entity.setParams(aggregate.getParamsJson());
        entity.setIdempotentKey(aggregate.getIdempotentKey());
        entity.setExprHash(aggregate.getExprHash());
        entity.setPriority(aggregate.getPriority());
        entity.setScheduledAt(aggregate.getScheduledAt());
        entity.setLastHeartbeatAt(aggregate.getLastHeartbeatAt());
        entity.setRetryCount(aggregate.getRetryCount());
        entity.setLastErrorCode(aggregate.getLastErrorCode());
        entity.setLastErrorMsg(aggregate.getLastErrorMsg());
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
        return TaskAggregate.restore(
                entity.getId(),
                entity.getScheduleInstanceId(),
                entity.getPlanId(),
                entity.getSliceId(),
                entity.getProvenanceCode(),
                entity.getOperationCode(),
                entity.getParams(),
                entity.getIdempotentKey(),
                entity.getExprHash(),
                entity.getPriority(),
                entity.getScheduledAt(),
                entity.getLastHeartbeatAt(),
                entity.getRetryCount(),
                entity.getLastErrorCode(),
                entity.getLastErrorMsg(),
                status,
                version);
    }
}
