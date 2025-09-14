package com.patra.ingest.infra.mapstruct;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.patra.ingest.domain.model.aggregate.Task;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import org.mapstruct.*;

/**
 * 采集任务转换器 · DO ↔ Domain
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface JobConverter {

    @Mapping(target = "params", expression = "java(toJsonString(src.getParams()))")
    Task toAggregate(TaskDO src);

    @Mapping(target = "params", expression = "java(toJsonNode(src.getParams()))")
    TaskDO toDO(Task src);

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
