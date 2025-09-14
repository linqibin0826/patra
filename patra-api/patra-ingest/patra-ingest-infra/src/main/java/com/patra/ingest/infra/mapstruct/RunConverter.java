package com.patra.ingest.infra.mapstruct;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.infra.persistence.entity.TaskRunDO;
import org.mapstruct.*;

/**
 * 任务运行（attempt）转换器 · DO ↔ Domain
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RunConverter {

    @Mappings({
            @Mapping(target = "checkpoint", expression = "java(toJsonString(src.getCheckpoint()))"),
            @Mapping(target = "stats", expression = "java(toJsonString(src.getStats()))")
    })
    TaskRun toEntity(TaskRunDO src);

    @Mappings({
            @Mapping(target = "checkpoint", expression = "java(toJsonNode(src.getCheckpoint()))"),
            @Mapping(target = "stats", expression = "java(toJsonNode(src.getStats()))")
    })
    TaskRunDO toDO(TaskRun src);

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
