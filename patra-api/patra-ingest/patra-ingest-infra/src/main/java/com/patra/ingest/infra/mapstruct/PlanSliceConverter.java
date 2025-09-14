package com.patra.ingest.infra.mapstruct;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.patra.ingest.domain.model.entity.PlanSlice;
import com.patra.ingest.infra.persistence.entity.PlanSliceDO;
import org.mapstruct.*;

/**
 * 计划切片转换器 · DO ↔ Domain
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlanSliceConverter {

    @Mappings({
            @Mapping(target = "sliceSpec", expression = "java(toJsonString(src.getSliceSpec()))"),
            @Mapping(target = "exprSnapshot", expression = "java(toJsonString(src.getExprSnapshot()))")
    })
    PlanSlice toEntity(PlanSliceDO src);

    @Mappings({
            @Mapping(target = "sliceSpec", expression = "java(toJsonNode(src.getSliceSpec()))"),
            @Mapping(target = "exprSnapshot", expression = "java(toJsonNode(src.getExprSnapshot()))")
    })
    PlanSliceDO toDO(PlanSlice src);

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
