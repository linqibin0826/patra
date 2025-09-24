package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;
import org.springframework.stereotype.Component;

@Component
public class ScheduleInstanceConverter {

    public ScheduleInstanceDO toDO(ScheduleInstanceAggregate aggregate) {
        ScheduleInstanceDO entity = new ScheduleInstanceDO();
        entity.setId(aggregate.getId());
        entity.setSchedulerCode(aggregate.getSchedulerCode() == null ? null : aggregate.getSchedulerCode().name());
        entity.setSchedulerJobId(aggregate.getSchedulerJobId());
        entity.setSchedulerLogId(aggregate.getSchedulerLogId());
        entity.setTriggerTypeCode(aggregate.getTriggerType() == null ? null : aggregate.getTriggerType().name());
        entity.setTriggeredAt(aggregate.getTriggeredAt());
        entity.setProvenanceCode(aggregate.getProvenanceCode() == null ? null : aggregate.getProvenanceCode().getCode());
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
                entity.getSchedulerCode() == null ? null : com.patra.ingest.domain.model.enums.SchedulerCode.valueOf(entity.getSchedulerCode()),
                entity.getSchedulerJobId(),
                entity.getSchedulerLogId(),
                entity.getTriggerTypeCode() == null ? null : com.patra.ingest.domain.model.enums.TriggerType.valueOf(entity.getTriggerTypeCode()),
                entity.getTriggeredAt(),
                entity.getProvenanceCode() == null ? null : com.patra.common.enums.ProvenanceCode.parse(entity.getProvenanceCode()),
                version);
    }
}
