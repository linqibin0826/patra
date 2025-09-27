package com.patra.ingest.infra.persistence.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ScheduleInstanceConverter {
    TypeReference<java.util.Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

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
        entity.setTriggerParams(toJsonNode(aggregate.getTriggerParams()));
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
                toParamMap(entity.getTriggerParams()),
                entity.getProvenanceCode() == null ? null : ProvenanceCode.parse(entity.getProvenanceCode()),
                version);
    }

    default JsonNode toJsonNode(java.util.Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        return JsonMapperHolder.getObjectMapper().valueToTree(params);
    }

    default java.util.Map<String, Object> toParamMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return java.util.Collections.emptyMap();
        }
        return JsonMapperHolder.getObjectMapper().convertValue(node, MAP_TYPE);
    }
}
