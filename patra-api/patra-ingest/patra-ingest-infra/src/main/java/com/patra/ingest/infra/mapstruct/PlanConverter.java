package com.patra.ingest.infra.mapstruct;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.patra.ingest.domain.model.aggregate.Plan;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import org.mapstruct.*;

/**
 * 采集计划转换器 · DO ↔ Domain
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlanConverter {

    @Mappings({
            @Mapping(target = "exprProtoSnapshot", expression = "java(toJsonString(src.getExprProtoSnapshot()))"),
            @Mapping(target = "sliceParams", expression = "java(toJsonString(src.getSliceParams()))")
    })
    Plan toAggregate(PlanDO src);

    @Mappings({
            @Mapping(target = "exprProtoSnapshot", expression = "java(toJsonNode(src.getExprProtoSnapshot()))"),
            @Mapping(target = "sliceParams", expression = "java(toJsonNode(src.getSliceParams()))")
    })
    PlanDO toDO(Plan src);

    // helpers
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
