package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;
import org.springframework.stereotype.Component;

@Component
public class ScheduleInstanceConverter {

    public ScheduleInstanceDO toDO(ScheduleInstanceAggregate aggregate) {
        ScheduleInstanceDO entity = new ScheduleInstanceDO();
        entity.setId(aggregate.getId());
        entity.setSchedulerCode(aggregate.getSchedulerCode());
        entity.setSchedulerJobId(aggregate.getSchedulerJobId());
        entity.setSchedulerLogId(aggregate.getSchedulerLogId());
        entity.setTriggerTypeCode(aggregate.getTriggerTypeCode());
        entity.setTriggeredAt(aggregate.getTriggeredAt());
        entity.setProvenanceCode(aggregate.getProvenanceCode());
        entity.setProvenanceConfigSnapshot(aggregate.getProvenanceConfigSnapshotJson());
        entity.setExprProtoHash(aggregate.getExprProtoHash());
        entity.setExprProtoSnapshot(aggregate.getExprProtoSnapshotJson());
        entity.setVersion(aggregate.getVersion());
        return entity;
    }

    public ScheduleInstanceAggregate toDomain(ScheduleInstanceDO entity) {
        if (entity == null) {
            return null;
        }
        long version = entity.getVersion() == null ? 0L : entity.getVersion();
        return ScheduleInstanceAggregate.restore(
                entity.getId(),
                entity.getSchedulerCode(),
                entity.getSchedulerJobId(),
                entity.getSchedulerLogId(),
                entity.getTriggerTypeCode(),
                entity.getTriggeredAt(),
                entity.getProvenanceCode(),
                entity.getProvenanceConfigSnapshot(),
                entity.getExprProtoHash(),
                entity.getExprProtoSnapshot(),
                version);
    }
}
