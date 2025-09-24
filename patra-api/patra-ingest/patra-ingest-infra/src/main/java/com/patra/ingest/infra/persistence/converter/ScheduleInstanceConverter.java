package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;
import org.springframework.stereotype.Component;

@Component
public class ScheduleInstanceConverter {

    public ScheduleInstanceDO toDO(ScheduleInstanceAggregate aggregate) {
        ScheduleInstanceDO entity = new ScheduleInstanceDO();
        entity.setId(aggregate.getId());
        // 使用字典编码持久化到 scheduler_code
        entity.setSchedulerCode(aggregate.getScheduler() == null ? null : aggregate.getScheduler().getCode());
        entity.setSchedulerJobId(aggregate.getSchedulerJobId());
        entity.setSchedulerLogId(aggregate.getSchedulerLogId());
        // 使用触发类型字典编码
        entity.setTriggerTypeCode(aggregate.getTriggerType() == null ? null : aggregate.getTriggerType().getCode());
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
                // 从 scheduler_code 解析为枚举，忽略大小写与空白
                entity.getSchedulerCode() == null ? null : Scheduler.fromCode(entity.getSchedulerCode()),
                entity.getSchedulerJobId(),
                entity.getSchedulerLogId(),
                entity.getTriggerTypeCode() == null ? null : TriggerType.fromCode(entity.getTriggerTypeCode()),
                entity.getTriggeredAt(),
                entity.getProvenanceCode() == null ? null : com.patra.common.enums.ProvenanceCode.parse(entity.getProvenanceCode()),
                version);
    }
}
