package com.patra.ingest.infra.mapstruct;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.infra.persistence.entity.TaskRunBatchDO;
import org.mapstruct.*;

/**
 * 运行批次转换器 · DO ↔ Domain
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RunBatchConverter {

    @Mapping(target = "stats", expression = "java(toJsonString(src.getStats()))")
    TaskRunBatch toEntity(TaskRunBatchDO src);

    @Mapping(target = "stats", expression = "java(toJsonNode(src.getStats()))")
    TaskRunBatchDO toDO(TaskRunBatch src);

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
