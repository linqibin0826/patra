package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import org.springframework.stereotype.Component;

/**
 * 任务聚合与数据对象转换。
 */
@Component
public class TaskConverter {

    public TaskDO toEntity(TaskAggregate aggregate) {
        TaskDO entity = new TaskDO();
        entity.setId(aggregate.getId());
        entity.setScheduleInstanceId(aggregate.getScheduleInstanceId());
        entity.setPlanId(aggregate.getPlanId());
        entity.setSliceId(aggregate.getSliceId());
        entity.setProvenanceCode(aggregate.getProvenanceCode());
        entity.setOperationCode(aggregate.getOperationCode());
        entity.setCredentialId(aggregate.getCredentialId());
        entity.setParams(aggregate.getParamsJson());
        entity.setIdempotentKey(aggregate.getIdempotentKey());
        entity.setExprHash(aggregate.getExprHash());
        entity.setPriority(aggregate.getPriority());
        entity.setScheduledAt(aggregate.getScheduledAt());
        entity.setStatusCode(aggregate.getStatus().name());
        entity.setVersion(aggregate.getVersion());
        return entity;
    }

    public TaskAggregate toAggregate(TaskDO entity) {
        if (entity == null) {
            return null;
        }
        TaskStatus status = entity.getStatusCode() == null
                ? TaskStatus.QUEUED
                : TaskStatus.valueOf(entity.getStatusCode());
        long version = entity.getVersion() == null ? 0L : entity.getVersion();
        return TaskAggregate.restore(
                entity.getId(),
                entity.getScheduleInstanceId(),
                entity.getPlanId(),
                entity.getSliceId(),
                entity.getProvenanceCode(),
                entity.getOperationCode(),
                entity.getCredentialId(),
                entity.getParams(),
                entity.getIdempotentKey(),
                entity.getExprHash(),
                entity.getPriority(),
                entity.getScheduledAt(),
                status,
                version);
    }
}
