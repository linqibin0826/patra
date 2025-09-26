package com.patra.ingest.infra.persistence.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ScheduleInstanceConverter {
    default ScheduleInstanceDO toDO(ScheduleInstanceAggregate aggregate) {
        if (aggregate == null) {
            return null;
        }
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
        // 触发参数：目前 domain 使用 Map<String,Object>，DO 是 JsonNode，暂置空或由上层填充
        entity.setTriggerParams((JsonNode) null);
        entity.setVersion(aggregate.getVersion());
        return entity;
    }

    default ScheduleInstanceAggregate toDomain(ScheduleInstanceDO entity) {
        if (entity == null) {
            return null;
        }
        long version = entity.getVersion() == null ? 0L : entity.getVersion();
        return ScheduleInstanceAggregate.restore(
                entity.getId(),
                // 从 scheduler_code 解析为枚举
                entity.getSchedulerCode() == null ? null : Scheduler.fromCode(entity.getSchedulerCode()),
                entity.getSchedulerJobId(),
                entity.getSchedulerLogId(),
                entity.getTriggerTypeCode() == null ? null : TriggerType.fromCode(entity.getTriggerTypeCode()),
                entity.getTriggeredAt(),
                null, // triggerParams TODO: 由上层决定是否需要反序列化
                entity.getProvenanceCode() == null ? null : ProvenanceCode.parse(entity.getProvenanceCode()),
                version);
    }
}
