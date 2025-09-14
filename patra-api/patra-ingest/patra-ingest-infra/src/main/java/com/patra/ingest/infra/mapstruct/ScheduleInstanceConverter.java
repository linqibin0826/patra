package com.patra.ingest.infra.mapstruct;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.patra.ingest.domain.model.aggregate.ScheduleInstance;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;
import org.mapstruct.*;

/**
 * 调度实例 · DO ↔ Domain 转换器。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ScheduleInstanceConverter {

    // DO -> Domain
    @Mappings({
            @Mapping(target = "triggerParams", expression = "java(toJsonString(src.getTriggerParams()))"),
            @Mapping(target = "provenanceConfigSnapshot", expression = "java(toJsonString(src.getProvenanceConfigSnapshot()))"),
            @Mapping(target = "exprProtoSnapshot", expression = "java(toJsonString(src.getExprProtoSnapshot()))")
    })
    ScheduleInstance toAggregate(ScheduleInstanceDO src);

    // Domain -> DO
    @Mappings({
            @Mapping(target = "triggerParams", expression = "java(toJsonNode(src.getTriggerParams()))"),
            @Mapping(target = "provenanceConfigSnapshot", expression = "java(toJsonNode(src.getProvenanceConfigSnapshot()))"),
            @Mapping(target = "exprProtoSnapshot", expression = "java(toJsonNode(src.getExprProtoSnapshot()))")
    })
    ScheduleInstanceDO toDO(ScheduleInstance src);

    // Helpers
    default String toJsonString(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return node.isTextual() ? node.asText() : node.toString();
    }

    default JsonNode toJsonNode(String json) {
        if (json == null || json.isBlank()) return NullNode.getInstance();
        try {
            return new ObjectMapper().readTree(json);
        } catch (Exception e) {
            return NullNode.getInstance();
        }
    }
}

